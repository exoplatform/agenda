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
      event = event && event.event || event;
      this.$root.$emit('agenda-event-details', event);
    },
    viewDay({ date }) {
      this.focus = date;
      this.type = 'day';
    }
  }
};
</script>
