<template>
  <v-flex class="event-form-dates d-flex flex-column">
    <v-toolbar
      class="border-color mb-4"
      max-height="64"
      flat>
      <div class="d-flex flex-row align-start col-sm-3 col-2">
        <agenda-connector-status
          class="my-auto"
          :connectors="connectors">
          <template slot="connectButton">
            <v-btn class="btn">
              <i class="uiIconHyperlink mr-2 darkGreyIcon"></i>
              {{ $t('agenda.connectYourPersonalAgenda') }}
            </v-btn>
          </template>
        </agenda-connector-status>
        <v-progress-circular
          v-if="loading"
          indeterminate
          color="primary"
          size="20"
          class="ml-3 my-auto" />
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
        <div
          v-if="!eventObj.event || eventObj.event.type !== 'remoteEvent'"
          :class="eventObj.event.dateOption && 'editing-event' || ''"
          class="readonly-event">
          <p
            :title="eventObj.event.summary"
            class="text-truncate my-auto ml-2 caption font-weight-bold d-flex">
            <span class="text-truncate mr-auto">{{ eventObj.event.summary }}</span>
            <v-icon
              v-if="eventObj.event.dateOption && allowMultipleDates"
              color="white"
              class="my-auto py-0 pr-0"
              size="18"
              @click="deleteDateOption(eventObj)">
              close
            </v-icon>
          </p>
          <div v-if="!eventObj.event.allDay && !isShortEvent(eventObj)" class="d-flex">
            <date-format
              :value="eventObj.event.startDate"
              :format="timeFormat"
              class="ml-2" />
            <strong
              class="mx-1">-</strong>
            <date-format
              :value="eventObj.event.endDate"
              :format="timeFormat"
              class="mr-2" />
          </div>
        </div>
        <agenda-connector-remote-event-item
          v-else
          :remote-event="eventObj.event"
          :avatar="connectedConnectorAvatar" />
      </template>
      <template #day-body="day">
        <div
          class="v-current-time"
          :class="{ today: day.present }"
          :style="currentTimeStyle"></div>
      </template>
    </v-calendar>
    <agenda-connectors-drawer :connectors="connectors" />
  </v-flex>
</template>

<script>

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
    allowMultipleDates: false,
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
    spaceEvents: [],
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
      return this.connectors.find(connector => connector.isSignedIn);
    },
    connectedConnectorAvatar() {
      return this.connectedConnector && this.connectedConnector.avatar || '';
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
          const index = remoteEventsToDisplay.findIndex(remoteEvent => remoteEvent.id && remoteEvent.id === event.remoteId || remoteEvent.recurringEventId && remoteEvent.recurringEventId === event.remoteId);
          if (index >= 0) {
            remoteEventsToDisplay.splice(index, 1);
          }
        });
      }
      return remoteEventsToDisplay;
    },
  },
  watch: {
    displayedEvents() {
      window.setTimeout(() => {
        this.adjustEditingEventsZIndex();
      }, 200);
    },
    connectedConnector() {
      this.retrieveRemoteEvents();
    },
  },
  created() {
    if(!this.event.timeZoneId) {
      this.event.timeZoneId = this.$agendaUtils.USER_TIMEZONE_ID;
    }

    this.$agendaUtils.initEventForm(this.event, true);

    this.$root.$emit('agenda-connectors-init');

    this.$featureService.isFeatureEnabled('agenda.datePoll')
      .then(enabled => this.allowMultipleDates = enabled);
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
      if (this.allowMultipleDates) {
        this.event.dateOptions.push(dateOption);
      } else {
        this.event.dateOptions = [dateOption];
      }
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

        if(endDate - this.newStartedEvent.startDate < this.$agendaUtils.MINIMUM_TIME_INTERVAL_MS) {
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
      if(event && event.startDate && event.endDate) {
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
    retrieveRemoteEvents() {
      if(this.connectedConnector) {
        const startEventRFC3359 = this.$agendaUtils.toRFC3339(this.period.start, false, true);
        const endEventRFC3359 = this.$agendaUtils.toRFC3339(this.period.end, false, true);

        this.loading = true;
        this.connectedConnector.getEvents(startEventRFC3359, endEventRFC3359)
          .then(events => {
            events.forEach(event => {
              this.$agendaUtils.convertDates(event);
            });
            this.remoteEvents = events;
            this.loading = false;
          }).catch(error => {
            console.error('Error retrieving remote events', error);
            this.loading = false;
          })
          .finally(() => this.refreshEventsToDisplay());
      } else {
        this.remoteEvents = [];
        this.refreshEventsToDisplay();
      }
    },
    retrieveEventsFromStore() {
      const userIdentityId = this.eventType === 'myEvents' && eXo.env.portal.userIdentityId || null;

      const calendarOwner = this.event && this.event.calendar && this.event.calendar.owner;

      let ownerIdentityPromise = null;
      if (calendarOwner && calendarOwner.remoteId && calendarOwner.providerId) {
        ownerIdentityPromise = this.$identityService.getIdentityByProviderIdAndRemoteId(calendarOwner.providerId, calendarOwner.remoteId);
      } else {
        ownerIdentityPromise = Promise.resolve(calendarOwner);
      }
      ownerIdentityPromise
        .then(ownerIdentity => {
          const ownerIds = ownerIdentity && ownerIdentity.id ? [ownerIdentity.id] : [];
          return this.$eventService.getEvents(null, ownerIds, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.$agendaUtils.toRFC3339(this.period.end), this.limit, null);
        })
        .then(data => {
          const events = data && data.events || [];
          events.forEach(event => {
            event.name = event.summary;
            this.$agendaUtils.convertDates(event);
          });
          this.spaceEvents = events;
          return this.$nextTick();
        })
        .catch(error =>{
          console.error('Error retrieving events', error);
        })
        .finally(() => this.refreshEventsToDisplay());
    },
    refreshEventsToDisplay() {
      const events = this.event.dateOptions || [];
      this.displayedEvents = [...events,...this.spaceEventsToDisplay, ...this.remoteEventsToDisplay];
    },
  },
};
</script>
