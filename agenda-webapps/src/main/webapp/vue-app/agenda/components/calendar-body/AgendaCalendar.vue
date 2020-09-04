<template>
  <div>
    <v-calendar
      ref="calendar"
      v-model="selectedDate"
      :events="events"
      :event-color="getEventColor"
      :event-timed="isEventTimed"
      :type="calendarType"
      :weekdays="weekdays"
      :show-week="true"
      event-name="summary"
      event-start="startDate"
      event-end="endDate"
      color="primary"
      @click:event="showEvent"
      @click:more="viewDay"
      @click:date="viewDay"
      @change="retrievePeriodEvents" />
    <agenda-event-preview-dialog />
  </div>
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
  },
  methods:{
    retrievePeriodEvents(range) {
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
    showEvent() {
      // TODO
    },
    viewDay({ date }) {
      this.focus = date;
      this.type = 'day';
    }
  }
};
</script>
