<template>
  <div class="d-flex">
    <select
      v-model="recurrenceType"
      class="ignore-vuetify-classes my-auto">
      <option
        v-for="recurrenceOption in recurrenceOptions"
        :key="recurrenceOption.value"
        :value="recurrenceOption.value">
        {{ recurrenceOption.text }}
      </option>
    </select>
    <agenda-event-form-recurrence-drawer
      ref="customRecurrentEventDrawer"
      :event-recurrence="customEventRecurrence"
      @apply="applyCustomEventRecurrence"
      @cancel="cancelCustomEventRecurrence" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      recurrenceType: 'NO_REPEAT',
      customEventRecurrence: {},
    };
  },
  computed: {
    eventRecurrence() {
      return this.event && this.event.parent ? this.event.parent.recurrence : this.event.recurrence;
    },
    recurrenceOptions() {
      return [{
        text: this.$t('agenda.doNotRepeat'),
        value: 'NO_REPEAT'
      },
      {
        text: this.$t('agenda.daily'),
        value: 'DAILY'
      },
      {
        text: this.$t('agenda.weekly',{ 0 : this.localizedDayNamefromDate }),
        value: 'WEEKLY'
      },
      {
        text: this.$t('agenda.monthly',{ 0 : this.localizedDayNamefromDate }),
        value: 'MONTHLY'
      },
      {
        text: this.$t('agenda.annually',{ 0 : this.localizedMonthFromDate , 1: this.dayNumberInMonth}),
        value: 'YEARLY'
      },
      {
        text: this.$t('agenda.everyWeekDay'),
        value: 'EVERY WEEKDAY'
      },
      {
        text: this.$t('agenda.custom'),
        value: 'CUSTOM'
      }];
    },
    localizedDayNamefromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getDayNameFromDate(day, eXo.env.portal.language);
    },
    localizedMonthFromDate(){
      const day = this.event.start;
      return this.$agendaUtils.getMonthFromDate(day, eXo.env.portal.language);
    },
    dayNamefromDate () {
      const day = this.event.start;
      return this.$agendaUtils.getDayNameFromDate(day);
    },
    monthFromDate(){
      const day = this.event.start;
      return this.$agendaUtils.getMonthFromDate(day);
    },
    dayNumberFromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getDayNumberFromDate(day);
    },
    monthNumberFromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getMonthNumberFromDate(day);
    },
    dayNumberInMonth() {
      const date = this.event.start;
      let d = null;
      if (date) {
        d = new Date(date);
      } else {
        d = new Date();
      }
      return d.getDate();
    },
    getDayNumberInYear() {
      const day = this.event.start;
      return this.$agendaUtils.getDayOfYear(day);
    },
  },
  watch: {
    recurrenceType(newVal, oldVal) {
      if (newVal && oldVal && newVal !== oldVal) {
        if (this.recurrenceType === 'CUSTOM') {
          this.$refs.customRecurrentEventDrawer.open();
        } else if (this.recurrenceType === 'NO_REPEAT') {
          this.event.recurrence = null;
        } else {
          this.event.recurrence = {
            type: this.recurrenceType,
            frequency: this.recurrenceType,
            interval: 1,
          };
          if (this.recurrenceType === 'WEEK_DAYS') {
            this.event.recurrence.frequency = 'WEEKLY';
            this.event.recurrence.byDay = ['SU','MO','TU','WE','TH','FR','SA'];
          } else if (this.recurrenceType === 'WEEKLY') {
            this.event.recurrence.byDay = [this.dayNamefromDate.substring(0,2).toUpperCase()];
          } else if (this.recurrenceType === 'MONTHLY') {
            this.event.recurrence.byMonthDay = [this.dayNumberInMonth];
          } else if(this.recurrenceType === 'YEARLY') {
            this.event.recurrence.byMonthDay = [this.dayNumberInMonth];
            this.event.recurrence.byMonth = [this.monthNumberFromDate];
          } else {
            this.event.recurrence = null;
          }
        }
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-form-opened', () => {
      this.$nextTick().then(() => this.reset());
    });
  },
  methods:{
    applyCustomEventRecurrence() {
      this.event.recurrence = JSON.parse(JSON.stringify(this.customEventRecurrence));
      this.event.recurrence.type = 'CUSTOM';
    },
    cancelCustomEventRecurrence() {
      this.reset();
    },
    reset() {
      this.resetRecurrenceType();
      this.resetCustomEventRecurrence();
    },
    resetRecurrenceType() {
      // Set to null before to not trigger changing event.recurrence
      // From 'watch' expression
      this.recurrenceType = null;

      this.$nextTick().then(() => {
        this.recurrenceType = this.eventRecurrence && this.eventRecurrence.type || 'NO_REPEAT';
        this.$forceUpdate();
      });
    },
    resetCustomEventRecurrence() {
      if (this.eventRecurrence && this.eventRecurrence.type) {
        this.customEventRecurrence = JSON.parse(JSON.stringify(this.eventRecurrence));
      } else {
        this.customEventRecurrence = {
          frequency: 'WEEKLY',
          interval: 1,
          byDay: ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'],
        };
      }
    },
  }
};
</script>