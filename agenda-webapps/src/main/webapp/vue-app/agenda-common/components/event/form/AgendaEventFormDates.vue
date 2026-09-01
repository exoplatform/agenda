<template>
  <v-flex class="event-form-dates d-flex flex-column">
    <v-toolbar
      class="border-color mb-4"
      max-height="64"
      flat>
      <!-- The header's compact row: the loading spinner, then the coverage
           counter, on one line and vertically centred with each other and with
           the week navigation beside them. align-center, not align-start:
           EXO-89845 was an alignment defect on this very header, and inline
           items of unequal height do not line up on their own.

           No connect affordance here, by EXO-89869: a calendar account is
           connected once, from the settings, not in the middle of creating an
           event — and with the personal-calendar drawer and the free/busy
           coverage now on this step, a plug offering to connect one is noise.
           What an already-connected account holds still paints on this grid;
           that comes from `signedInConnectors` reading the `connectors` prop,
           not from anything drawn in this header. -->
      <div class="d-flex flex-row align-center col-sm-3 col-2">
        <v-progress-circular
          v-if="loading"
          indeterminate
          color="primary"
          size="20"
          class="my-auto" />
        <agenda-event-form-busy-coverage
          variant="counter"
          :participants="participants"
          :checked-keys="checkedParticipantKeys"
          :not-disclosed-keys="notDisclosedParticipantKeys"
          :failed-keys="failedParticipantKeys"
          class="ms-2 my-auto" />
        <extension-registry-components
          :params="params"
          name="AgendaEventForm"
          type="agenda-event-form-toolbar"
          parent-element="div"
          element="div" />
      </div>
      <v-row
        align="center"
        justify="center"
        class="row d-flex flex-row flex-nowrap col-sm-6 col-8">
        <v-btn
          icon
          @click="prevDate">
          <i class="uiIconArrowLeft uiIconMedium darkGreyIcon"></i>
        </v-btn>
        <div class="period-title text-uppercase">
          {{ periodTitle }}
        </div>
        <v-btn
          icon
          color="grey darken-2"
          @click="nextDate">
          <i class="uiIconArrowRight uiIconMedium darkGreyIcon"></i>
        </v-btn>
      </v-row>
      <agenda-time-zone-select-box
        :event="event"
        class="align-end my-auto col-sm-3 col-2" />
    </v-toolbar>
    <!-- the grid behind the drag is what the user checks a slot against, so
         when an account could not be read the form says so where the choice
         is made, and says what it means for the choice: the slot may look
         free only because nobody could ask that account -->
    <v-alert
      v-if="hasFailedSource"
      type="warning"
      class="mb-4"
      dense
      outlined
      text>
      {{ failedSourceWarning }}
    </v-alert>
    <!-- Directly above the grid, and directly under the account warning: both
         say what this grid is NOT showing, and the organiser has to have read
         both before the drag starts, not after scrolling past the calendar.

         Body text, and only rendered when there is something to own up to —
         so the quiet case costs the grid no space at all, and the case that
         could mislead somebody is impossible to miss. -->
    <agenda-event-form-busy-coverage
      variant="report"
      :participants="participants"
      :checked-keys="checkedParticipantKeys"
      :not-disclosed-keys="notDisclosedParticipantKeys"
      :failed-keys="failedParticipantKeys" />
    <v-calendar
      ref="calendar"
      v-model="dayToDisplay"
      :events="eventsToDisplay"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :weekdays="weekdays"
      :interval-style="agendaIntervalStyle"
      :event-ripple="false"
      :locale="lang"
      event-name="summary"
      event-start="startDate"
      event-end="endDate"
      color="primary"
      type="week"
      @click:event="cancelClick"
      @mousedown:event="cancelClick"
      @mousedown:day="startTime"
      @mousemove:day="mouseMove"
      @mouseup:day="endDrag"
      @mousedown:time="startTime"
      @mousemove:time="mouseMove"
      @mouseup:time="endDrag"
      @change="retrieveEvents">
      <template #event="eventObj">
        <agenda-event-form-participant-busy-item
          v-if="eventObj.event && eventObj.event.type === 'participantBusy'"
          :busy-event="eventObj.event" />
        <div
          v-else-if="!eventObj.event || eventObj.event.type !== 'remoteEvent'"
          :class="eventObj.event.dateOption && 'editing-event' || ''"
          class="readonly-event">
          <p
            :title="storeEventTitle(eventObj.event)"
            class="text-truncate my-auto ms-2 caption font-weight-bold d-flex">
            <span class="text-truncate me-auto">{{ eventObj.event.summary }}</span>
            <v-icon
              v-if="eventObj.event.dateOption"
              color="white"
              class="my-auto py-0 pe-0"
              size="18"
              @click="deleteDateOption(eventObj)">
              close
            </v-icon>
          </p>
          <div v-if="!eventObj.event.allDay && !isShortEvent(eventObj)" class="d-flex">
            <date-format
              :value="eventObj.event.startDate"
              :format="timeFormat"
              class="ms-2" />
            <strong
              class="mx-1">-</strong>
            <date-format
              :value="eventObj.event.endDate"
              :format="timeFormat"
              class="me-2" />
          </div>
        </div>
        <agenda-connector-remote-event-item
          v-else
          :remote-event="eventObj.event"
          :connector="eventObj.event.connector || connectedConnector" />
      </template>
      <template #day-body="day">
        <div
          class="v-current-time"
          :class="{ today: day.present }"
          :style="currentTimeStyle"></div>
      </template>
    </v-calendar>
  </v-flex>
</template>

<script>

/*
 * The responses that make an event a commitment on the organiser's week. An
 * invitation they have not answered still occupies the slot as far as picking
 * one goes; a declined event does not. Same set EXO-89840 settled on for the
 * conflicts panel.
 */
const SCHEDULED_RESPONSES = ['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE'];

export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
    event: {
      type: Object,
      default: () => null,
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    periodTitle: '',
    loading: false,
    lang: eXo.env.portal.language,
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    dayToDisplay: Date.now(),
    dateTimeFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
    currentTimeTop: null,
    scrollToTimeTop: null,
    newStartedEvent: null,
    period: {},
    remoteEvents: [],
    /*
     * The accounts whose last read failed. Kept apart from the events because
     * an account that could not answer is not an account that answered
     * "nothing": the grid behind the drag is what the user checks a slot
     * against, and it has to say when it is not showing everything.
     */
    failedConnectors: [],
    /*
     * What eXo's own calendars hold over the previewed week. Two reads feed
     * it — the calendar being scheduled into, and everything the organiser is
     * an attendee of — and each of them fails on its own.
     */
    spaceEvents: [],
    /*
     * The calendar sources whose last read failed, as message keys. Kept
     * apart from the events for the reason EXO-89842 established: a source
     * that could not answer is not a source that answered "nothing".
     */
    failedStoreSourceKeys: [],
    /*
     * Only the newest store read may write what the grid shows.
     */
    storeRequestId: 0,
    /*
     * The third source behind the grid: the participants' busy time. It is
     * held as three separate things on purpose — the blocks that can be drawn,
     * and the two sets of people about whom nothing is known — because an
     * organiser about to pick a slot needs to be told the second and third as
     * plainly as they are shown the first.
     */
    participantBusyEvents: [],
    /*
     * The three sets, keyed by participant (providerId:remoteId), never by
     * identity id: a participant whose identity could not be resolved has no
     * id and still has to be nameable.
     */
    checkedParticipantKeys: [],
    notDisclosedParticipantKeys: [],
    failedParticipantKeys: [],
    /*
     * Resolved identities, memoised by participant key across calendar
     * navigations. A participant added through the suggester carries no
     * identity id until the event is saved, so one lookup per person is
     * needed — but only one: paging the grid a week forward must not re-ask
     * for everybody. Same memoisation EXO-89825 applies to calendar listings.
     */
    resolvedParticipants: {},
    /*
     * Only the newest read may write what the strip shows. Resolution is
     * asynchronous and now sits in front of the fetch, so two navigations in
     * quick succession can land out of order.
     */
    busyTimeRequestId: 0,
    displayedEvents: [],
  }),
  computed: {
    // A workaround to display events that finishes at midnight the same day
    eventsToDisplay() {
      const eventsToDisplay = [];
      this.displayedEvents.forEach(event => {
        if (event.endDate && event.endDate.toString().indexOf('00:00:00') >= 0) {
          const eventToDisplay = JSON.parse(JSON.stringify(event));
          eventToDisplay.startDate = this.$agendaUtils.toDate(event.startDate);
          eventToDisplay.endDate = this.$agendaUtils.toDate(event.endDate);
          eventToDisplay.endDate = new Date(eventToDisplay.endDate.getTime() - 60000);
          eventsToDisplay.push(eventToDisplay);
        } else {
          eventsToDisplay.push(event);
        }
      });
      return eventsToDisplay;
    },
    nowTimeOptions() {
      const now = new Date();
      return {hour: now.getHours(), minute: now.getMinutes()};
    },
    currentTimeStyle() {
      return `top: ${this.currentTimeTop}px;`;
    },
    connectedConnector() {
      return this.connectors.find(connector => connector.connected);
    },
    /**
     * The connected connectors whose browser session can be asked for remote
     * events: several accounts can be connected at once, and this preview
     * must show what every one of them holds.
     *
     * @returns {Array} the signed-in connected connectors
     */
    signedInConnectors() {
      return (this.connectors || [])
        .filter(connector => connector.connected && connector.isSignedIn);
    },
    /**
     * The set of accounts to fetch from, as a comparable string, so the
     * watcher fires when an account joins or leaves rather than on every
     * mutation of the connector objects.
     *
     * @returns {String} the signed-in connector names, joined
     */
    signedInConnectorNames() {
      return this.signedInConnectors.map(connector => connector.name).join(',');
    },
    spaceEventsToDisplay() {
      // Avoid to have same event that we are changing twice
      return this.spaceEvents && this.spaceEvents.filter(event => !this.isSameEvent(event)) || [];
    },
    remoteEventsToDisplay() {
      const remoteEventsToDisplay = this.remoteEvents && this.remoteEvents.slice() || [];
      // Avoid to have same event from remote and local store (pushed events from local store)
      if (this.spaceEvents.length && remoteEventsToDisplay.length) {
        this.spaceEvents.forEach(event => {
          const index = remoteEventsToDisplay.findIndex(remoteEvent => remoteEvent.id && remoteEvent.id === event.remoteId || (remoteEvent.recurringEventId && remoteEvent.recurringEventId === event.parent?.remoteId));
          if (index >= 0) {
            remoteEventsToDisplay.splice(index, 1);
          }
        });
      }
      return remoteEventsToDisplay;
    },
    params() {
      return {
        event: this.event,
        events: this.eventsToDisplay,
      };
    },
    /**
     * The sources the grid could not read, named as the user knows them:
     * connected accounts by the address they are signed in as, eXo's own
     * calendars by what they are.
     *
     * <p>
     * One list and one warning rather than two: the organiser is about to
     * pick a slot against this grid, and what they need to know is that
     * something behind it is missing — not which layer of the product it
     * came from.
     *
     * @returns {Array} one display name per unreachable source
     */
    failedSourceNames() {
      return this.$agendaUtils.failedSourceNames(this.failedConnectors)
        .concat(this.failedStoreSourceKeys.map(key => this.$t(key)));
    },
    /**
     * Whether the grid behind the drag is missing what an account holds.
     *
     * @returns {Boolean} true when at least one account could not be read
     */
    hasFailedSource() {
      return this.failedSourceNames.length > 0;
    },
    /**
     * What the warning above the grid says. It states the consequence, not
     * the incident: the user is about to pick a slot against this grid, so
     * the sentence they need is that the slot may not be free, not that a
     * request failed.
     *
     * @returns {String} the warning sentence
     */
    failedSourceWarning() {
      return this.$t('agenda.eventForm.busyTimesIncomplete', {
        0: this.failedSourceNames.join(', '),
      });
    },
    /**
     * The people this grid undertakes to show the busy time of: the event's
     * user attendees, minus the organiser themselves.
     *
     * <p>
     * <strong>Selected on the provider/remote pair, never on the identity
     * id.</strong> A participant added through the suggester carries only
     * `{providerId, remoteId, profile}` until the event is saved — social's
     * `convertSuggesterItemToIdentity` builds exactly that — so a filter on
     * `identity.id` keeps nobody but the organiser, who is then removed by the
     * next filter, and the list of a NEW event is always empty. That is the
     * one population this feature exists for, and it was the whole of what
     * shipped in the first cut of EXO-89850.
     *
     * <p>
     * The organiser is left out because their own calendars are already
     * painted here by the other sources; asking for them again would draw
     * every one of their events a second time, in grey, on top of itself. They
     * are matched on their username as well as on their id, since on a new
     * event neither side of that comparison is guaranteed to carry one.
     *
     * <p>
     * A space attendee is left out too: a space has no busy time of its own,
     * and expanding it into its members would disclose the calendars of people
     * the organiser never named.
     *
     * @returns {Array} the participants, in attendee order
     */
    participants() {
      const currentUserIdentityId = String(eXo.env.portal.userIdentityId || '');
      const currentUserName = String(eXo.env.portal.userName || '');
      return (this.event && this.event.attendees || [])
        .filter(attendee => !!this.$agendaUtils.participantKey(attendee))
        .filter(attendee => attendee.identity.providerId !== 'space')
        .filter(attendee => String(attendee.identity.id || '') !== currentUserIdentityId
                         && String(attendee.identity.remoteId || '') !== currentUserName);
    },
    /**
     * The participants this screen last asked about, as a comparable string,
     * so the watcher fires when somebody joins or leaves the event rather than
     * on every mutation of the attendee objects.
     *
     * @returns {String} the participant keys, joined
     */
    participantKeys() {
      return this.participants.map(participant => this.$agendaUtils.participantKey(participant)).join(',');
    },
  },
  watch: {
    displayedEvents() {
      window.setTimeout(() => {
        this.adjustEditingEventsZIndex();
      }, 200);
    },
    /**
     * Fetches again when the set of accounts able to serve remote events
     * changes, so the preview follows connections and disconnections.
     * @returns {void}
     */
    signedInConnectorNames() {
      this.retrieveRemoteEvents();
    },
    /**
     * Asks again when the guest list changes, so that adding somebody on the
     * first step and coming back here shows their busy time — and, just as
     * importantly, so that removing somebody stops the screen naming them.
     * @returns {void}
     */
    participantKeys() {
      this.retrieveParticipantsBusyTime();
    },
  },
  created() {
    if (!this.event.timeZoneId) {
      this.event.timeZoneId = this.$agendaUtils.USER_TIMEZONE_ID;
    }

    this.$agendaUtils.initEventForm(this.event, true);

    this.$root.$emit('agenda-connectors-init');
  },
  mounted() {
    if (this.$refs.calendar) {
      this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
    }
    this.scrollToEvent();
  },
  methods: {
    adjustEditingEventsZIndex() {
      // A JS trick to force displaying events that are edited
      // displayed on top of the readonly events
      $('div:has(>.editing-event)').css('z-index', '1');
    },
    scrollToEvent() {
      const dateOption = this.event.dateOptions && this.event.dateOptions.length && this.event.dateOptions[0] || this.event;
      const dateToScrollTo = dateOption && dateOption.startDate || Date.now();
      const dateTime = this.$agendaUtils.toDate(dateToScrollTo);
      this.scrollToTimeTop = this.$refs.calendar.timeToY({
        hour: dateTime.getHours(),
        minute: dateTime.getMinutes(),
      });
      this.dayToDisplay = dateTime.getTime();
      this.$refs.calendar.updateTimes();
      this.scrollToTime();
    },
    scrollToTime() {
      this.$nextTick().then(() => {
        const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
        if (dailyScrollElement) {
          const scrollY = this.scrollToTimeTop - dailyScrollElement.offsetHeight / 2;
          dailyScrollElement.scrollTo(0, scrollY);
        }
      });
    },
    deleteDateOption(eventObj) {
      const dateOption = eventObj.event;
      const index = this.event.dateOptions.indexOf(dateOption);
      if (index >= 0) {
        this.event.dateOptions.splice(index, 1);
        this.$emit('date-option-deleted');
        this.$nextTick().then(() => {
          this.refreshEventsToDisplay();
          this.$refs.calendar.updateTimes();
          this.$forceUpdate();
        });
      }
    },
    cancelClick({nativeEvent}) {
      nativeEvent.stopPropagation();
      nativeEvent.preventDefault();
    },
    startTime(tms) {
      const startDate = this.$agendaUtils.toDateTime(tms);
      const endDate = startDate + this.$agendaUtils.MINIMUM_TIME_INTERVAL_MS;

      const dateOption = {
        dateOption: true,
        eventId: this.event.id,
        summary: this.event.summary,
        color: this.getEventColor(this.event, true),
        occurrence: this.event.occurrence,
        parent: {
          id: this.event.parent && this.event.parent.id,
        },
        allDay: !tms.hasTime,
        startDate,
        endDate,
      };
      this.newStartedEvent = dateOption;
      this.event.dateOptions.push(dateOption);
      this.$emit('date-option-added');

      //refresh after assigning a startDate for the new event for the first time only
      this.$nextTick().then(() => {
        this.refreshEventsToDisplay();
        this.$refs.calendar.updateTimes();
        this.$forceUpdate();
      });
    },
    mouseMove(tms) {
      if (this.newStartedEvent) {
        const mouse = this.$agendaUtils.toDateTime(tms);
        let endDate = this.$agendaUtils.roundTime(mouse);

        if (this.newStartedEvent.endDate !== endDate) {
          this.$nextTick().then(() => {
            this.refreshEventsToDisplay();
          });
        }

        if (endDate - this.newStartedEvent.startDate < this.$agendaUtils.MINIMUM_TIME_INTERVAL_MS) {
          endDate = this.newStartedEvent.startDate + this.$agendaUtils.MINIMUM_TIME_INTERVAL_MS;
        }
        this.newStartedEvent.endDate = endDate;

        this.$refs.calendar.updateTimes();
        this.$forceUpdate();
      }
    },
    endDrag() {
      if (this.newStartedEvent) {
        this.newStartedEvent.start = this.$agendaUtils.toRFC3339(this.newStartedEvent.startDate);
        this.newStartedEvent.end = this.$agendaUtils.toRFC3339(this.newStartedEvent.endDate);

        this.newStartedEvent = null;
        this.$nextTick().then(() => {
          this.refreshEventsToDisplay();
          this.$refs.calendar.updateTimes();
          this.$forceUpdate();
        });
      }
    },
    isShortEvent(eventObj) {
      const event = eventObj && eventObj.event || eventObj;
      if (event && event.startDate && event.endDate) {
        return this.$agendaUtils.isShortEvent(event);
      } else {
        return true;
      }
    },
    isSameEvent(event) {
      return event === this.event
      || (event.id && event.id === this.event.id)
      || (event.eventId && event.eventId === this.event.id)
      || (event.parent && this.event.parent && event.parent.id === this.event.parent.id
          && event.occurrence && this.event.occurrence && event.occurrence.id === this.event.occurrence.id);
    },
    getEventColor(event, noOpacity) {
      const eventColor = event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
      if (noOpacity || event.dateOption || this.isSameEvent(event)){
        return eventColor;
      } else {
        return this.$agendaUtils.addOpacity(eventColor, 40);
      }
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    nextDate() {
      if (this.$refs.calendar) {
        this.$refs.calendar.next();
      }
    },
    prevDate() {
      if (this.$refs.calendar) {
        this.$refs.calendar.prev();
      }
    },
    retrieveEvents(range) {
      if (range) {
        this.retrievePeriod(range);
        this.retrieveEventsFromStore();
        this.retrieveRemoteEvents();
        this.retrieveParticipantsBusyTime();
      }
      this.$forceUpdate();
    },
    retrievePeriod(range) {
      range.end = JSON.parse(JSON.stringify(range.end));
      // End of the day of end date
      range.end.hour = 23;
      range.end.minute = 59;
      // Start of the day of start date
      range.start.hour = 0;
      range.start.minute = 0;
      this.period = this.$agendaUtils.convertVuetifyRangeToPeriod(range);
      if (this.period) {
        this.period.title = this.$refs.calendar.title;
        this.periodTitle = this.$agendaUtils.generateCalendarTitle('week', this.$agendaUtils.toDate(this.period.start), this.period.title, this.$t('agenda.week'));
      }
    },
    agendaIntervalStyle(interval) {
      if (this.workingTime.showWorkingTime) {
        const inactive = interval.weekday === 0 ||
            interval.weekday === 6 ||
            interval.time < this.workingTime.workingTimeStart ||
            interval.time >= this.workingTime.workingTimeEnd;
        const startOfHour = interval.minute === 0;
        const dark = this.dark;
        const mid = dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)';

        return {
          backgroundColor: inactive ? dark ? 'rgba(0,0,0,0.4)' : 'rgba(0,0,0,0.05)' : null,
          borderTop: startOfHour ? null : `1px dashed ${mid}`,
        };
      } else {
        return null;
      }
    },
    /**
     * Fetches the remote events of every signed-in connected account for the
     * previewed period and merges them into one deduplicated array, each
     * event tagged with the account it came from.
     *
     * @returns {void}
     */
    retrieveRemoteEvents() {
      // The signed-in-connectors watcher fires as soon as a connector signs
      // in, which here is usually before the calendar's first @change has
      // built the period at all, so both bounds are still absent and the read
      // is rejected by strict connectors (CalDAV). Skipping loses nothing:
      // retrieveEvents() calls back once retrievePeriod() has run.
      if (!this.period || !this.period.start || !this.period.end) {
        return;
      }
      if (this.signedInConnectors.length) {
        const startEventRFC3359 = this.$agendaUtils.toRFC3339(this.period.start, false, true);
        const endEventRFC3359 = this.$agendaUtils.toRFC3339(this.period.end, false, true);

        this.loading = true;
        // Every signed-in account is asked, and each fails on its own: one
        // unreachable account must not blank the events the others returned
        Promise.all(this.signedInConnectors.map(connector =>
          connector.getEvents(startEventRFC3359, endEventRFC3359)
            .then(answer => {
              // A connector may report a partial read: the events it did get,
              // beside the fact that it could not get all of them. Both halves
              // travel on, or the grid paints a slot free that is not.
              const read = this.$agendaUtils.readConnectorAnswer(answer);
              read.events.forEach(event => {
                this.$agendaUtils.convertDates(event);
              });
              return {connector, events: read.events, failed: read.failed};
            })
            .catch(error => {
              console.error('Error retrieving remote events', connector.name, error);
              // No events array at all: an account that could not answer must
              // not reach the grid as an account that answered "nothing",
              // which would paint the slot free and let the user book over a
              // meeting that is really there.
              return {connector, failed: true};
            })))
          .then(eventsByConnector => {
            const sources = this.$agendaUtils.splitRemoteEventResults(eventsByConnector);
            this.remoteEvents = sources.events;
            this.failedConnectors = sources.failedConnectors;
            this.loading = false;
          })
          .finally(() => this.refreshEventsToDisplay());
      } else {
        this.remoteEvents = [];
        this.failedConnectors = [];
        this.refreshEventsToDisplay();
      }
    },
    /**
     * Fetches what eXo's own calendars hold over the previewed week, from
     * <strong>two</strong> sources.
     *
     * <p>
     * This grid exists so the organiser can see what a slot would clash with,
     * and it used to ask one calendar: the owner of the event being created.
     * Create a poll in a space and it asked that space alone — personal
     * calendar, other spaces, and calendars materialised from a connected
     * account were all invisible at the moment of choosing. So the organiser
     * picked a slot over their own meeting and the screen never said a word.
     *
     * <p>
     * The two sources, and why neither replaces the other:
     * <ul>
     * <li><strong>the target calendar</strong> — everything owned by the
     * calendar being scheduled into, including events the organiser is not an
     * attendee of. A space meeting they were not invited to still occupies
     * that space's week, and the poll is being placed in it;</li>
     * <li><strong>the organiser's own commitments</strong> — everything they
     * are an attendee of, wherever it lives. This is EXO-89840's read,
     * unchanged: the same events endpoint already filters on the viewer as an
     * attendee, and it is also what answers for a materialised remote
     * calendar, whose events the live connector read stops reporting the
     * moment they are imported.</li>
     * </ul>
     *
     * <p>
     * An event in both — a space meeting the organiser attends — is drawn
     * once, the target calendar's copy winning, since that is the calendar
     * whose colour the grid is showing.
     *
     * <p>
     * <strong>Each source fails on its own and says so.</strong> One
     * unreachable source must not blank what the other returned, and it must
     * not reach the grid as a source that answered "nothing": that is
     * EXO-89842 and EXO-89843's contract, and it matters more here than
     * anywhere because the answer becomes a decision.
     *
     * @returns {void}
     */
    retrieveEventsFromStore() {
      const requestId = ++this.storeRequestId;
      const start = this.$agendaUtils.toRFC3339(this.period.start, true);
      const end = this.$agendaUtils.toRFC3339(this.period.end);
      Promise.all([this.readTargetCalendarEvents(start, end), this.readOwnEvents(start, end)])
        .then(sources => {
          if (requestId !== this.storeRequestId) {
            return;
          }
          this.spaceEvents = this.mergeStoreEvents(sources);
          this.failedStoreSourceKeys = sources.filter(source => source.failed).map(source => source.labelKey);
          return this.$nextTick();
        })
        // The terminal catch the single-source read had and the two-source
        // rewrite dropped. Both reads already handle their own failure, so
        // reaching here means something else broke — and nothing else broke
        // is not something this screen may assume, since the grid it draws
        // becomes a decision. Both sources are declared unread.
        .catch(error => {
          if (requestId !== this.storeRequestId) {
            return;
          }
          console.error('Error retrieving events', error);
          this.spaceEvents = [];
          this.failedStoreSourceKeys = ['agenda.eventForm.sourceThisCalendar', 'agenda.eventForm.sourceYourCalendars'];
        })
        .finally(() => {
          if (requestId === this.storeRequestId) {
            this.refreshEventsToDisplay();
          }
        });
    },
    /**
     * Reads the calendar the poll is being placed in.
     *
     * <p>
     * The owner is resolved the same way it always was. When the event carries
     * no calendar owner yet there is no target calendar to read, which is an
     * answer rather than a failure — the organiser's own commitments below
     * still cover the week.
     *
     * @param {String} start the period start, RFC3339
     * @param {String} end the period end, RFC3339
     * @returns {Promise} resolves with `{events, failed, labelKey}`
     */
    readTargetCalendarEvents(start, end) {
      const labelKey = 'agenda.eventForm.sourceThisCalendar';
      const calendarOwner = this.event && this.event.calendar && this.event.calendar.owner;
      const ownerIdentityPromise = calendarOwner && calendarOwner.remoteId && calendarOwner.providerId
        ? this.$identityService.getIdentityByProviderIdAndRemoteId(calendarOwner.providerId, calendarOwner.remoteId)
        : Promise.resolve(calendarOwner);
      return ownerIdentityPromise
        .then(ownerIdentity => {
          const ownerIds = ownerIdentity && ownerIdentity.id ? [ownerIdentity.id] : [];
          if (!ownerIds.length) {
            return {events: [], failed: false, labelKey};
          }
          return this.$eventService.getEvents(null, ownerIds, null, start, end, 0, null, 'attendees, conferences')
            .then(data => ({events: this.readStoreEvents(data), failed: false, labelKey}));
        })
        .catch(error => {
          console.error('Error retrieving the events of the calendar being scheduled into', error);
          // No events array: a calendar that could not answer must not reach
          // the grid as a calendar that answered "nothing".
          return {failed: true, labelKey};
        });
    },
    /**
     * Reads everything the organiser is an attendee of, across their
     * calendars.
     *
     * @param {String} start the period start, RFC3339
     * @param {String} end the period end, RFC3339
     * @returns {Promise} resolves with `{events, failed, labelKey}`
     */
    readOwnEvents(start, end) {
      const labelKey = 'agenda.eventForm.sourceYourCalendars';
      const attendeeIdentityId = eXo.env.portal.userIdentityId || null;
      if (!attendeeIdentityId) {
        return Promise.resolve({events: [], failed: false, labelKey});
      }
      return this.$eventService.getEvents(null, [], attendeeIdentityId, start, end, 0, SCHEDULED_RESPONSES, 'attendees, conferences')
        .then(data => ({events: this.readStoreEvents(data), failed: false, labelKey}))
        .catch(error => {
          console.error('Error retrieving the events of the user own calendars', error);
          return {failed: true, labelKey};
        });
    },
    /**
     * Normalises what the events endpoint answered for the grid.
     *
     * @param {Object} data the endpoint answer
     * @returns {Array} the events, with their dates converted
     */
    readStoreEvents(data) {
      const events = data && data.events || [];
      events.forEach(event => {
        event.name = event.summary;
        this.$agendaUtils.convertDates(event);
      });
      return events;
    },
    /**
     * Merges the two store reads into one list, each event once.
     *
     * <p>
     * Only a source that carries an events array contributes; one that failed
     * carries none, which is the whole point of keeping the two apart. The
     * first source to claim an id keeps it, so the target calendar's copy of a
     * shared event wins — an event with no id at all is kept rather than
     * folded away, since two absent ids are not a match.
     *
     * @param {Array} sources the per-source results
     * @returns {Array} the merged events
     */
    mergeStoreEvents(sources) {
      const seenIds = new Set();
      const merged = [];
      sources.forEach(source => (source.events || []).forEach(event => {
        const id = event.id && String(event.id) || '';
        if (id && seenIds.has(id)) {
          return;
        }
        if (id) {
          seenIds.add(id);
        }
        merged.push(event);
      }));
      return merged;
    },
    /**
     * What a grid block says on hover.
     *
     * <p>
     * Now that the grid carries several calendars, its colours mean something
     * the organiser was never given a legend for. Naming the calendar on hover
     * is the answer EXO-89825 already gave for the same problem on the
     * connected-account rows, and it costs no new visual vocabulary — the
     * colour keeps distinguishing, the title says what it distinguishes.
     *
     * @param {Object} event a grid event
     * @returns {String} the title attribute of the block
     */
    storeEventTitle(event) {
      const summary = event && event.summary || '';
      const calendar = event && event.calendar;
      if (!summary || !calendar) {
        return summary;
      }
      // The same rule AgendaPersonalCalendarList.calendarLabel applies: the
      // user-defined name when there is one, and 'My calendar' for the unnamed
      // default, whose title the server fills with the owner's display name.
      const calendarName = calendar.name
        || (calendar.system ? this.$t('agenda.myCalendar') : calendar.title || this.$t('agenda.myCalendar'));
      return this.$t('agenda.eventForm.eventInCalendar', {0: summary, 1: calendarName});
    },
    /**
     * Fetches the busy time of every participant for the previewed period —
     * the grid's third source, beside the organiser's own space events and
     * their connected accounts.
     *
     * <p>
     * The three outcomes the server distinguishes per person are kept
     * distinct here too: blocks to draw, people who share nothing, people
     * whose read broke. Only the first is merged into the grid; the other two
     * are handed to the coverage line above it, because a person missing from
     * the grid with nothing said about them is a person the organiser will
     * book over.
     *
     * <p>
     * <strong>A rejected read makes every participant failed, not free.</strong>
     * The whole call is one source, exactly like a connector that could not
     * answer (EXO-89843): when it fails, nothing is known about anybody, and
     * that is what the screen then says.
     *
     * <p>
     * One thing this cannot do, and it follows from what the endpoint refuses
     * to send: a block carries no event identifier, so when an existing event
     * is being rescheduled its own attendees show as busy over its current
     * slot, by that very event. Telling that block apart would need an id the
     * server deliberately never discloses.
     *
     * @returns {void}
     */
    retrieveParticipantsBusyTime() {
      // Same guard as the remote-event read: the watcher can fire before the
      // calendar's first @change has built the period at all.
      if (!this.period || !this.period.start || !this.period.end) {
        return;
      }
      const participants = this.participants;
      const requestId = ++this.busyTimeRequestId;
      if (!participants.length) {
        this.forgetParticipantsBusyTime();
        this.refreshEventsToDisplay();
        return;
      }
      const start = this.$agendaUtils.toRFC3339(this.period.start, false, true);
      const end = this.$agendaUtils.toRFC3339(this.period.end, false, true);
      this.resolveParticipants(participants)
        .then(resolved => {
          if (requestId !== this.busyTimeRequestId) {
            return null;
          }
          // A participant the platform could not put a name to is not a
          // participant who is free: they never reach the endpoint, so they
          // are declared unread here rather than quietly left out.
          const unresolvedKeys = participants
            .map(participant => this.$agendaUtils.participantKey(participant))
            .filter(key => !resolved[key]);
          const identityIds = Object.values(resolved).map(identity => String(identity.id));
          if (!identityIds.length) {
            this.participantBusyEvents = [];
            this.checkedParticipantKeys = [];
            this.notDisclosedParticipantKeys = [];
            this.failedParticipantKeys = unresolvedKeys;
            return null;
          }
          return this.$availabilityService.getBusyTime(identityIds, start, end)
            .then(records => ({records, resolved, unresolvedKeys}))
            .catch(error => {
              console.error('Error retrieving the participants busy time', error);
              // Nothing was read about anybody. Emptying the blocks WITHOUT
              // filling the failed list would repaint the grid as if every
              // participant were free.
              return {records: null, resolved, unresolvedKeys};
            });
        })
        .then(answer => {
          if (!answer || requestId !== this.busyTimeRequestId) {
            return;
          }
          this.applyParticipantsBusyTime(participants, answer);
        })
        // The chain ends in a catch, not in a finally. `finally` re-throws
        // what it was handed, so a chain terminated by one leaves any error
        // its handlers raised — a service that throws instead of rejecting, a
        // malformed payload — as an UNHANDLED rejection. In a browser that is
        // a console error; under the pinned Node the build runs on it kills
        // the process. And the honest degradation is the same one every other
        // failure here gets: nothing was read, so nobody is free.
        .catch(error => {
          if (requestId !== this.busyTimeRequestId) {
            return;
          }
          console.error('Error reading the participants busy time', error);
          this.participantBusyEvents = [];
          this.checkedParticipantKeys = [];
          this.notDisclosedParticipantKeys = [];
          this.failedParticipantKeys = participants
            .map(participant => this.$agendaUtils.participantKey(participant));
        })
        .finally(() => {
          if (requestId === this.busyTimeRequestId) {
            this.refreshEventsToDisplay();
          }
        });
    },
    /**
     * Clears everything the grid holds about the participants busy time.
     *
     * <p>
     * The three sets go together: emptying the blocks while leaving a set
     * behind would name somebody the grid is no longer showing anything for,
     * and emptying a set while leaving blocks would show a block the strip no
     * longer counts.
     *
     * @returns {void}
     */
    forgetParticipantsBusyTime() {
      this.participantBusyEvents = [];
      this.checkedParticipantKeys = [];
      this.notDisclosedParticipantKeys = [];
      this.failedParticipantKeys = [];
    },
    /**
     * Writes one answer onto the grid and the coverage strip.
     *
     * <p>
     * A null `records` is the whole read having failed: every participant that
     * was going to be asked about joins the ones that could not be resolved,
     * because in both cases nothing was read and neither is an answer.
     *
     * @param {Array} participants the people the read was about
     * @param {Object} answer `{records, resolved, unresolvedKeys}`
     * @returns {void}
     */
    applyParticipantsBusyTime(participants, answer) {
      const keyOf = participant => this.$agendaUtils.participantKey(participant);
      if (!answer.records) {
        this.participantBusyEvents = [];
        this.checkedParticipantKeys = [];
        this.notDisclosedParticipantKeys = [];
        this.failedParticipantKeys = participants.map(keyOf);
        return;
      }
      const split = this.$agendaUtils.splitBusyTimeResults(answer.records);
      const checked = [];
      const notDisclosed = [];
      const failed = answer.unresolvedKeys.slice();
      const busyEvents = [];
      participants.forEach(participant => {
        const key = keyOf(participant);
        const identity = answer.resolved[key];
        if (!identity) {
          return;
        }
        const identityId = String(identity.id);
        if (split.failedIds.includes(identityId)) {
          failed.push(key);
        } else if (split.notDisclosedIds.includes(identityId)) {
          notDisclosed.push(key);
        } else if (split.checkedIds.includes(identityId)) {
          checked.push(key);
          // The resolved identity carries a fuller profile than the suggester
          // left behind, so the avatar on each block is drawn from it.
          const blocks = split.busyByIdentityId[identityId];
          busyEvents.push(...this.$agendaUtils.toParticipantBusyEvents(blocks, {identity}, this.$t('agenda.busy')));
        } else {
          // Asked about and not answered for. Silence is not an answer.
          failed.push(key);
        }
      });
      this.checkedParticipantKeys = checked;
      this.notDisclosedParticipantKeys = notDisclosed;
      this.failedParticipantKeys = failed;
      this.participantBusyEvents = busyEvents;
    },
    /**
     * Puts an identity id on every participant that has none yet.
     *
     * <p>
     * A participant added through the suggester carries `{providerId,
     * remoteId}` and nothing else until the event is saved, and the
     * availability endpoint speaks identity ids. This resolves them with the
     * very call this component already makes for the calendar owner.
     *
     * <p>
     * <strong>Memoised by participant key, and only the unseen ones are
     * asked for.</strong> This runs on every calendar navigation; without the
     * memo, paging a month forward would re-resolve the whole guest list a
     * dozen times. A failed lookup is memoised as a failure too, so it is not
     * retried on every page either — the participant is named as unread
     * instead, which is the honest answer and a stable one.
     *
     * @param {Array} participants the people to resolve
     * @returns {Promise} resolves with `{[participantKey]: identity}`, holding
     *          only the participants that could be resolved
     */
    resolveParticipants(participants) {
      // hasOwnProperty rather than Object.hasOwn: the build pins Node 16.0.0,
      // which predates it (16.9), and so do the browsers before Chrome 93 /
      // Safari 15.4. The distinction the check needs is the same either way —
      // a key PRESENT with a null value is a lookup that already failed and
      // must not be retried, which a truthiness test would miss.
      const pending = participants.filter(participant => {
        const key = this.$agendaUtils.participantKey(participant);
        return key && !Object.prototype.hasOwnProperty.call(this.resolvedParticipants, key);
      });
      return Promise.all(pending.map(participant => {
        const key = this.$agendaUtils.participantKey(participant);
        const identity = participant.identity;
        if (identity.id) {
          this.resolvedParticipants[key] = identity;
          return Promise.resolve();
        }
        return this.$identityService.getIdentityByProviderIdAndRemoteId(identity.providerId, identity.remoteId)
          .then(resolvedIdentity => {
            // A lookup that answers without an id resolved nothing; recording
            // it as a success would send `undefined` to the endpoint.
            this.resolvedParticipants[key] = resolvedIdentity && resolvedIdentity.id && resolvedIdentity || null;
          })
          .catch(error => {
            console.error('Error resolving the identity of a participant', key, error);
            this.resolvedParticipants[key] = null;
          });
      })).then(() => {
        const resolved = {};
        participants.forEach(participant => {
          const key = this.$agendaUtils.participantKey(participant);
          if (this.resolvedParticipants[key]) {
            resolved[key] = this.resolvedParticipants[key];
          }
        });
        return resolved;
      });
    },
    refreshEventsToDisplay() {
      const events = this.event.dateOptions || [];
      this.displayedEvents = [...events,...this.spaceEventsToDisplay, ...this.remoteEventsToDisplay, ...this.participantBusyEvents];
    },
  },
};
</script>
