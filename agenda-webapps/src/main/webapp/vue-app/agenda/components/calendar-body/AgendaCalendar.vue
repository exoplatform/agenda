<template>
  <v-calendar
    ref="calendar"
    v-model="selectedDate"
    :events="events"
    :event-color="getEventColor"
    :event-timed="isEventTimed"
    :type="calendarType"
    :weekdays="weekdays"
    :first-time="agendaStartTime"
    :interval-count="agendaIntervalCount"
    event-name="summary"
    event-start="startDate"
    event-end="endDate"
    color="primary"
    show-week
    @click:event="showEvent"
    @click:more="viewDay"
    @click:date="viewDay"
    @mousedown:time="quickAddEventCreate"
    @mousedown:day="quickAddEventCreate"
    @mousemove:time="quickAddEventMove"
    @mousemove:day="quickAddEventMove"
    @mouseup:time="quickAddEventOpenDialog"
    @mouseup:day="quickAddEventOpenDialog"
    @change="retrievePeriodEvents">
    <template #day-body="day">
      <div
        class="v-current-time"
        :class="{ today: day.present }"
        :style="currentTimeStyle"></div>
    </template>
  </v-calendar>
</template>

<script>
export default {
  props: {
    events: {
      type: Array,
      default: null
    },
    calendarType: {
      type: String,
      default: null
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
    quickEvent: null,
    selectedDate: '',
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false,
    currentTimeTop: null,
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
    }
  },
  watch: {
    calendarType() {
      this.scrollToTime();
    },
    workingTime() {
      this.scrollToTime();
    }
  },
  mounted() {
    this.$root.$on('agenda-display-calendar-atDate', date => {
      this.selectedDate = date || '';
    });
    this.$root.$on('agenda-display-calendar-next', () => {
      this.$refs.calendar.next();
    });
    this.$root.$on('agenda-display-calendar-previous', () => {
      this.$refs.calendar.prev();
    });
    this.$root.$on('agenda-calendar-color-changed', (calendarId, calendarColor) => {
      this.events.forEach(event => {
        if (event.calendar.id === calendarId) {
          event.calendar.color = calendarColor;
        }
      });
      this.$forceUpdate();
    });
    this.$root.$on('agenda-event-quick-form-cancel', this.cancelCreateEvent);
    this.scrollToTime();
  },
  methods:{
    scrollToTime() {
      this.$nextTick().then(() => {
        const dailyScrollElement = document.querySelector('.v-calendar-daily__scroll-area');
        if (dailyScrollElement) {
          this.currentTimeTop = this.$refs.calendar.timeToY(this.nowTimeOptions);
          const scrollY = this.currentTimeTop - dailyScrollElement.offsetHeight / 2;
          dailyScrollElement.scrollTo(0, scrollY);
        }
      });
    },
    retrievePeriodEvents(range) {
      // In Vuetify, the 'start object' === 'end object',
      // this is a workaround to avoid changing end date
      // and impact start date as well, when we are in day view
      range.end = JSON.parse(JSON.stringify(range.end));

      // End of the day of end date
      range.end.hour = 23;
      range.end.minute = 59;

      const period = this.$agendaUtils.convertVuetifyRangeToPeriod(range, this.$userTimeZone);
      period.title = this.$refs.calendar.title;
      this.$root.$emit('agenda-change-period', period);
    },
    getEventColor(event) {
      return event.color || event.calendar && event.calendar.color || 'primary';
    },
    isEventTimed(event) {
      return event && !event.allDay;
    },
    showEvent(event) {
      this.cancelCreateEvent();
      event = event && event.event || event;
      this.$root.$emit('agenda-event-details', event);
    },
    viewDay({ date }) {
      this.focus = date;
      this.$root.$emit('agenda-change-period-type', 'day');
    },
    cancelCreateEvent() {
      if (this.quickEvent) {
        const index = this.events.findIndex(event => event === this.quickEvent);
        if (index >= 0) {
          this.events.splice(index, 1);
        }
        this.quickEvent = null;
      }
    },
    quickAddEventCreate(params) {
      if (!params || params.event) {
        return;
      }
      this.cancelCreateEvent();
      const startDate = this.toDate(params, false);
      this.quickEvent = {
        summary: '',
        startDate: startDate,
        endDate: startDate,
        allDay: !params.time,
        editing: true,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      };
    },
    quickAddEventMove(params) {
      if (this.quickEvent && this.quickEvent.editing) {

        const newDate = this.toDate(params);
        if (this.quickEvent.startDate.getTime() > newDate.getTime()) {
          this.quickEvent.startDate = this.toDate(params, false);
        } else {
          this.quickEvent.endDate = this.toDate(params, true);
        }

        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
      }
    },
    quickAddEventOpenDialog() {
      if (this.quickEvent) {
        if (!this.quickEvent.added) {
          this.quickEvent.added = true;
          this.events.push(this.quickEvent);
        }
        delete this.quickEvent.editing;
        this.$root.$emit('agenda-event-quick-form-open', this.quickEvent);
      }
    },
    roundTime(minute, down) {
      const roundTo = 15; // minutes

      return down
        ? minute - minute % roundTo
        : minute + (roundTo - minute % roundTo);
    },
    toDate(tms, down = true) {
      return new Date(tms.year, tms.month - 1, tms.day, tms.hour, this.roundTime(tms.minute, down));
    },
  }
};
</script>
