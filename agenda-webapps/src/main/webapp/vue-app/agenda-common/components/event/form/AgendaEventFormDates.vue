<template>
  <v-flex class="event-form-dates">
    <v-toolbar flat class="border-color mb-4">
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
      :events="displayedEvents"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :weekdays="weekdays"
      :interval-style="agendaIntervalStyle"
      :event-ripple="false"
      event-name="summary"
      event-start="startDate"
      event-end="endDate"
      color="primary"
      type="week"
      @click:event="showEvent"
      @mousedown:event="showEvent"
      @mousedown:time="startTime"
      @mousemove:time="mouseMove"
      @mouseup:time="endDrag"
      @mouseleave:time="endDrag"
      @change="retrieveEvents">
      <template #event="eventObj">
        <div
          v-if="eventObj.event.type !== 'remoteEvent'"
          :id="getEventDomId(eventObj)"
          class="v-event-draggable v-event-draggable-parent">
          <p
            class="text-truncate my-auto ml-2 caption font-weight-bold">
            {{ eventObj.event.summary }}
          </p>
          <div v-if="!eventObj.event.allDay && !isShortEvent(eventObj)" class="d-flex">
            <date-format
              :value="eventObj.event.startDate"
              :format="timeFormat"
              class="v-event-draggable ml-2" />
            <strong
              class="mx-1">-</strong>
            <date-format
              :value="eventObj.event.endDate"
              :format="timeFormat"
              class="v-event-draggable mr-2" />
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
    <v-menu
      ref="eventDatesMenu"
      v-model="selectedOpen"
      :close-on-content-click="false"
      :activator="selectedElement"
      :left="menuLeftPosition"
      :right="!menuLeftPosition"
      :top="menuTopPosition"
      content-class="select-date-pickers agenda-application"
      offset-x>
      <v-card
        min-width="350"
        class="pa-0 pb-4"
        flat>
        <v-card-text class="pa-0">
          <div class="d-flex">
            <v-btn
              class="ml-auto"
              color="grey"
              icon
              dark
              @click="selectedOpen = false">
              <v-icon>
                mdi-close
              </v-icon>
            </v-btn>
          </div>
          <agenda-event-form-date-pickers
            v-if="selectedOpen"
            ref="selectedEventDatePickers"
            :event="selectedEvent"
            :date-picker-top="datePickerTop"
            class="px-3"
            @changed="updateCalendarDisplay(selectedEvent)" />
        </v-card-text>
      </v-card>
    </v-menu>
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
      default: () => ({}),
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
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    selectedEvent: null,
    selectedElement: null,
    selectedOpen: null,
    datePickerTop: true,
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
    menuLeftPosition: false,
    menuTopPosition: false,
    period: {},
    remoteEvents: [],
    spaceEvents: [],
  }),
  computed: {
    nowTimeOptions() {
      const now = new Date();
      return {hour: now.getHours(), minute: now.getMinutes()};
    },
    currentTimeStyle() {
      return `top: ${this.currentTimeTop}px;`;
    },
    events() {
      return [...this.spaceEvents, this.event];
    },
    domId() {
      return `eventForm-${this.event.id}-${new Date(this.event.startDate).getTime()}`;
    },
    connectedConnector() {
      return this.connectors.find(connector => connector.isSignedIn);
    },
    connectedConnectorAvatar() {
      return this.connectedConnector && this.connectedConnector.avatar || '';
    },
    displayedEvents() {
      const eventsToDisplay = this.events && this.events.slice() || [];
      // Avoid to have same event that we are changing twice
      if (this.event && (this.event.id || this.event.parent)) {
        const index = eventsToDisplay.findIndex(event => this.isSameEvent(event, this.event));
        if (index >= 0) {
          eventsToDisplay.splice(index, 1);
        }
      }
      const remoteEventsToDisplay = this.remoteEvents && this.remoteEvents.slice() || [];
      // Avoid to have same event from remote and local store (pushed events from local store)
      if (eventsToDisplay.length && remoteEventsToDisplay.length) {
        eventsToDisplay.forEach(event => {
          const index = remoteEventsToDisplay.findIndex(remoteEvent => remoteEvent.id && remoteEvent.id === event.remoteId);
          if (index >= 0) {
            remoteEventsToDisplay.splice(index, 1);
          }
        });
      }
      return [...eventsToDisplay, ...remoteEventsToDisplay];
    },
  },
  watch: {
    connectedConnector() {
      this.retrieveRemoteEvents();
    },
    displayedEvents() {
      if (this.displayedEvents && this.displayedEvents.length) {
        this.selectedOpen = false;
        this.$nextTick(() => this.showEventDatePickers(this.event));
      }
    },
    selectedOpen() {
      if (this.selectedOpen && this.$refs.selectedEventDatePickers) {
        this.$refs.selectedEventDatePickers.reset();
      }
    },
  },
  created() {
    if(!this.event.timeZoneId) {
      this.event.timeZoneId = this.$agendaUtils.USER_TIMEZONE_ID;
    }
    if (!this.event.startDate) {
      this.event.startDate = this.event.start && this.$agendaUtils.toDate(this.event.start) || new Date();
      this.event.startDate = this.roundTime(new Date(this.event.startDate).getTime());
    }

    if (!this.event.endDate) {
      if (this.event.end) {
        this.event.endDate = this.$agendaUtils.toDate(this.event.end).getTime();
      } else {
        this.event.endDate = new Date(this.event.startDate).getTime();
      }
    }

    this.$forceUpdate();
    this.$root.$emit('agenda-connectors-init');
  },
  mounted() {
    if (this.$refs.calendar) {
      this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
      this.scrollToEvent(this.event);
      window.setTimeout(() => {
        this.showEventDatePickers(this.event);
      }, 200);
    }
    this.$root.$on('agenda-event-save', () => {
      this.selectedEvent = null;
      this.selectedOpen = false;
    });
  },
  methods: {
    updateCalendarDisplay(event) {
      event.startDate = new Date(this.event.startDate);
      event.endDate = new Date(this.event.endDate);
      event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      event.end = this.$agendaUtils.toRFC3339(this.event.endDate);
      this.scrollToEvent(event);
      this.retrieveEvents();
      if (this.$refs.eventDatesMenu) {
        this.showEventDatePickers(event, 200);
      }
    },
    scrollToEvent(event) {
      const dateTime = this.$agendaUtils.toDate(event.startDate);
      this.scrollToTimeTop = this.$refs.calendar.timeToY({
        hour: dateTime.getHours(),
        minute: dateTime.getMinutes(),
      });
      this.dayToDisplay = event.startDate ? this.$agendaUtils.toDate(event.startDate).getTime(): this.$agendaUtils.toDate(event.start).getTime();
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
    showEvent({nativeEvent, event}) {
      if (!nativeEvent) {
        return;
      }
      nativeEvent.preventDefault();
      nativeEvent.stopPropagation();

      if (!event.created) {
        this.showEventDatePickers(event);
      }
    },
    showEventDatePickers(event, waitTimeToDisplay = 200) {
      const domId = this.getEventDomId(event);
      let $targetElement = $(`#${domId}`);
      const targetElement = $targetElement.length && $targetElement[0];

      this.newEventStarted = false;
      this.selectedOpen = false;
      this.selectedElement = targetElement;
      this.selectedEvent = event;

      window.setTimeout(() => {
        if (this.selectedEvent) {
          const domId = this.getEventDomId(event);
          $targetElement = $(`#${domId}`);
          if (!this.selectedElement) {
            this.selectedElement = $targetElement.length && $targetElement[0];
          }
          if ($targetElement && $targetElement.length && $targetElement.offset()) {
            this.datePickerTop = $targetElement.offset().top > 330;
            this.menuTopPosition = window.innerHeight - $targetElement.offset().top < 400;
            this.menuLeftPosition = $targetElement.offset().left > window.innerWidth / 2;
            this.selectedOpen = true;
          }
        } else {
          this.selectedOpen = false;
        }
      }, waitTimeToDisplay);
    },
    startTime(tms) {
      const mouse = this.toTime(tms);
      this.event.startDate = this.roundTime(mouse);
      this.event.endDate = this.$agendaUtils.toDate(this.event.startDate);
      this.newEventStarted = true;
      this.selectedOpen = false;

      this.$forceUpdate();
    },
    mouseMove(tms) {
      if (this.newEventStarted) {
        const mouse = this.toTime(tms);
        this.event.endDate = this.roundTime(mouse);
        this.retrieveEvents();
      }
    },
    endDrag() {
      if (this.newEventStarted) {
        this.newEventStarted = false;
        this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
        this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);
        this.selectedOpen = false;
        this.selectedEvent = null;
        this.$nextTick().then(() => {
          this.retrieveEvents();
          this.showEventDatePickers(this.event);
        });
      }
    },
    getEventDomId(eventObj) {
      const event = eventObj && eventObj.event || eventObj;
      return `eventForm-${event.id || (event.parent && event.parent.id) || ''}-${new Date(event.startDate).getTime()}`;
    },
    isShortEvent(eventObj) {
      const event = eventObj && eventObj.event || eventObj;
      return this.$agendaUtils.isShortEvent(event);
    },
    isSameEvent(event1, event2) {
      return event1 === event2 || (event1.id && event1.id === event2.id)
      || (event1.parent && event2.parent && event1.parent.id === event2.parent.id
          && event1.occurrence && event2.occurrence && event1.occurrence.id === event2.occurrence.id);
    },
    getEventColor(event) {
      const eventColor = event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
      if (this.isSameEvent(event, this.event)){
        return eventColor;
      } else {
        return this.$agendaUtils.addOpacity(eventColor, 40);
      }
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    nextDate() {
      this.$refs.calendar.next();
    },
    prevDate() {
      this.$refs.calendar.prev();
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
        this.periodTitle = this.$agendaUtils.generateCalendarTitle('week', new Date(this.period.start), this.period.title, this.$t('agenda.week'));
      }
    },
    roundTime(time, down = true) {
      const roundTo = 15; // minutes
      const roundDownTime = roundTo * 60 * 1000;

      return down
        ? time - time % roundDownTime
        : time + (roundDownTime - time % roundDownTime);
    },
    toTime(tms) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, tms.minute).getTime();
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
          });
      } else {
        this.remoteEvents = [];
      }
    },
    retrieveEventsFromStore() {
      const userIdentityId = this.eventType === 'myEvents' && eXo.env.portal.userIdentityId || null;
      this.$eventService.getEvents(null, [], userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.$agendaUtils.toRFC3339(this.period.end), this.limit, null)
        .then(data => {
          const events = data && data.events || [];
          events.forEach(event => {
            event.name = event.summary;
            this.$agendaUtils.convertDates(event);
          });
          this.spaceEvents = events;
        }).catch(error =>{
          console.error('Error retrieving events', error);
        });
    }
  },
};
</script>
