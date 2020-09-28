<template>
  <v-flex class="event-form-dates">
    <v-toolbar flat class="border-color mb-4">
      <v-switch
        v-model="event.allDay"
        :label="$t('agenda.allDay')" />
      <v-row
        align="center"
        justify="center"
        class="flex-nowrap">
        <v-btn
          icon
          @click="prevDate">
          <i class="uiIconArrowLeft uiIconMedium text-color"></i>
        </v-btn>
        <div class="period-title text-uppercase">
          {{ periodTitle }}
        </div>
        <v-btn
          icon
          color="grey darken-2"
          @click="nextDate">
          <i class="uiIconArrowRight uiIconMedium text-color"></i>
        </v-btn>
      </v-row>
    </v-toolbar>
    <v-calendar
      ref="calendar"
      :events="displayedEvents"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :start="dayToDisplay"
      :weekdays="weekdays"
      :first-time="agendaStartTime"
      :interval-count="agendaIntervalCount"
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
      @change="retrieveEvents">
      <template #event="eventObj">
        <div :id="getEventDomId(eventObj)" class="v-event-draggable v-event-draggable-parent">
          <div class="d-flex flex-nowrap">
            <strong class="text-truncate">{{ eventObj.event.summary }}</strong>
            <template v-if="!eventObj.event.allDay">
              <date-format
                :value="eventObj.event.startDate"
                :format="timeFormat"
                class="ml-2" />
              <strong class="mx-2">-</strong>
              <date-format
                :value="eventObj.event.endDate"
                :format="timeFormat"
                class="mr-2" />
            </template>
          </div>
        </div>
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
      content-class="select-date-pickers agenda-application"
      offset-x>
      <v-card min-width="350" flat>
        <v-card-text>
          <agenda-event-form-date-pickers
            v-if="selectedEvent"
            ref="selectedEventDatePickers"
            :event="selectedEvent"
            :date-picker-top="datePickerTop"
            @changed="updateCalendarDisplay(selectedEvent)" />
        </v-card-text>
      </v-card>
    </v-menu>
  </v-flex>
</template>

<script>
export default {
  props: {
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
    dragEvent: null,
    dragStart: null,
    createEvent: null,
    createStart: null,
    extendOriginal: null,
    selectedEvent: null,
    selectedElement: null,
    selectedOpen: null,
    datePickerTop: true,
    displayedEvents: [],
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
    timezoneDiff: null,
  }),
  computed: {
    nowTimeOptions() {
      const now = new Date();
      return {hour: now.getHours(), minute: now.getMinutes()};
    },
    currentTimeStyle() {
      return `top: ${this.currentTimeTop}px;`;
    },
    agendaStartTime() {
      return this.workingTime.showWorkingTime ? this.workingTime.workingTimeStart : '00:00';
    },
    agendaIntervalCount() {
      return this.workingTime.showWorkingTime ? parseInt(this.workingTime.workingTimeEnd) - parseInt(this.workingTime.workingTimeStart) : '24';
    },
    events() {
      return this.event && [this.event] || [];
    },
    domId() {
      return `eventForm-${this.event.id}-${this.event.startDate.getTime()}`;
    },
  },
  watch: {
    selectedOpen() {
      if (this.selectedOpen && this.$refs.selectedEventDatePickers) {
        this.$refs.selectedEventDatePickers.reset();
      }
    },
  },
  created() {
    if (!this.event.startDate) {
      this.event.startDate = this.event.start && new Date(this.event.start) || new Date();
      this.event.startDate = new Date(this.roundTime(this.event.startDate.getTime()));
    }
    if (!this.event.endDate) {
      if (this.event.end) {
        this.event.endDate = new Date(this.event.end);
      } else {
        this.event.endDate = this.event.startDate;
      }
    }
  },
  mounted() {
    this.timezoneDiff =  eXo.env.portal.timezoneOffset + new Date().getTimezoneOffset() * 60000;
    if (this.$refs.calendar) {
      this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
      this.scrollToEvent(this.event);
      window.setTimeout(() => {
        this.showEventDatePickers(this.event);
      }, 200);
    }
  },
  methods: {
    updateCalendarDisplay(event) {
      event.start = this.$agendaUtils.toRFC3339(new Date(this.event.startDate));
      event.end = this.$agendaUtils.toRFC3339(new Date(this.event.endDate));
      this.scrollToEvent(event);
      this.retrieveEvents();
      if (this.$refs.eventDatesMenu) {
        this.showEventDatePickers(event, 200);
      }
    },
    scrollToEvent(event) {
      const dateTime = new Date(event.startDate);
      this.scrollToTimeTop = this.$refs.calendar.timeToY({
        hour: dateTime.getHours(),
        minute: dateTime.getMinutes(),
      });
      this.dayToDisplay = event.startDate.getTime();
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

      this.showEventDatePickers(event);
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
        if (!this.selectedElement) {
          const domId = this.getEventDomId(event);
          $targetElement = $(`#${domId}`);
          this.selectedElement = $targetElement.length && $targetElement[0];
        }
        if (this.selectedElement) {
          this.datePickerTop = $(`#${domId}`).offset().top > 330;
        }
        this.selectedOpen = true;
      }, waitTimeToDisplay);
    },
    startTime(tms) {
      const mouse = this.toTime(tms);
      this.event.startDate = this.roundTime(mouse);
      this.event.endDate = new Date(this.event.startDate);
      this.newEventStarted = true;
      this.selectedOpen = false;
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
        this.event.start = this.$agendaUtils.toRFC3339(new Date(this.event.startDate));
        this.event.end = this.$agendaUtils.toRFC3339(new Date(this.event.endDate));
        this.selectedOpen = false;
        this.selectedEvent = null;
        this.retrieveEvents();
        this.showEventDatePickers(this.event);
      }
    },
    getEventDomId(eventObj) {
      const event = eventObj && eventObj.event || eventObj;
      return `eventForm-${event.id}-${new Date(event.startDate).getTime()}`;
    },
    getEventColor(event) {
      return event && (event.color || event.calendar && event.calendar.color) || '#2196F3';
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
      this.displayedEvents = this.events.slice();
      if (range) {
        this.retrievePeriod(range);
      }
      this.$forceUpdate();
    },
    retrievePeriod(range) {
      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range, this.$userTimeZone);
      if (period) {
        period.title = this.$refs.calendar.title;
        this.periodTitle = this.$agendaUtils.generateCalendarTitle('week', new Date(period.start), period.title, this.$t('agenda.header.toolbar.title.week'));
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
  },
};
</script>
