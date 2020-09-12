<template>
  <v-calendar
    ref="calendar"
    v-model="selectedDate"
    :events="events"
    :event-color="getEventColor"
    :event-timed="isEventTimed"
    :type="calendarType"
    :weekdays="weekdays"
    event-name="summary"
    event-start="startDate"
    event-end="endDate"
    color="primary"
    show-week
    @click:event="showEvent"
    @click:more="viewDay"
    @click:date="viewDay"
    @change="retrievePeriodEvents" />
</template>

<script>
export default {
  props: {
    calendarType: {
      type: String,
      default: null
    },
    events: {
      type: Array,
      default: null
    },
    weekdays: {
      type: Array,
      default: () => null
    },
  },
  data: () => ({
    selectedDate: '',
    selectedEvent: {},
    selectedElement: null,
    selectedOpen: false
  }),
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
  },
  methods:{
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
