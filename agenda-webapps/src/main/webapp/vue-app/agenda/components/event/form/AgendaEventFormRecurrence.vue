<template>
  <div class="d-flex">
    <select
      v-model="recurrenceType"
      class="subtitle-1 ignore-vuetify-classes my-auto">
      <option
        v-for="recurrenceOption in recurrenceOptions"
        :key="recurrenceOption.value"
        :value="recurrenceOption.value">
        {{ recurrenceOption.text }}
      </option>
    </select>
    <v-btn
      v-if="recurrenceType === 'CUSTOM'"
      icon
      class="ml-2 my-auto"
      @click="openCustomRecurrenceForm">
      <i class="uiIcon uiIconEdit  primary--text"></i>
    </v-btn>
    <agenda-event-form-recurrence-drawer
      ref="customRecurrentEventDrawer"
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
    };
  },
  computed: {
    recurrenceOptions() {
      const dayNumberInMonth = this.dayNumberInMonth;
      const localizedMonthFromDate = this.localizedMonthFromDate;
      const localizedDayNamefromDate = this.localizedDayNamefromDate;

      return [{
        text: this.$t('agenda.doNotRepeat'),
        value: 'NO_REPEAT'
      },
      {
        text: this.$t('agenda.daily'),
        value: 'DAILY'
      },
      {
        text: this.$t('agenda.workingWeekDay'),
        value: 'WEEK_DAYS'
      },
      {
        text: this.$t('agenda.weekly',{ 0 : localizedDayNamefromDate }),
        value: 'WEEKLY'
      },
      {
        text: this.$t('agenda.monthly',{ 0 : localizedDayNamefromDate }),
        value: 'MONTHLY'
      },
      {
        text: this.$t('agenda.yearly',{ 0 : localizedMonthFromDate , 1: dayNumberInMonth}),
        value: 'YEARLY'
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
          this.openCustomRecurrenceForm();
        } else if (this.recurrenceType === 'NO_REPEAT') {
          this.event.recurrence = null;
          if (this.event.parent) {
            this.event.parent.recurrence = null;
          }
        } else {
          this.event.recurrence = {
            type: this.recurrenceType,
            frequency: this.recurrenceType,
            interval: 1,
          };
          if (this.recurrenceType === 'WEEK_DAYS') {
            this.event.recurrence.frequency = 'WEEKLY';
            this.event.recurrence.byDay = ['MO','TU','WE','TH','FR'];
          } else if (this.recurrenceType === 'WEEKLY') {
            this.event.recurrence.byDay = [this.dayNamefromDate.substring(0,2).toUpperCase()];
          } else if (this.recurrenceType === 'MONTHLY') {
            this.event.recurrence.byDay = [this.dayNamefromDate.substring(0,2).toUpperCase()];
          } else if(this.recurrenceType === 'YEARLY') {
            this.event.recurrence.byMonthDay = [this.dayNumberInMonth];
            this.event.recurrence.byMonth = [this.monthNumberFromDate];
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
    applyCustomEventRecurrence(eventRecurrence) {
      this.event.recurrence = JSON.parse(JSON.stringify(eventRecurrence));
      this.event.recurrence.type = 'CUSTOM';
    },
    cancelCustomEventRecurrence() {
      if (this.event.recurrence && this.event.recurrence.type !== 'CUSTOM') {
        this.reset();
      }
    },
    reset() {
      this.resetRecurrenceType();
    },
    resetRecurrenceType() {
      // Set to null before to not trigger changing event.recurrence
      // From 'watch' expression
      this.recurrenceType = null;

      this.$nextTick().then(() => {
        const eventRecurrence = this.event && this.event.recurrence || this.event.parent && this.event.parent.recurrence;
        this.recurrenceType = eventRecurrence && eventRecurrence.type || 'NO_REPEAT';
        this.$forceUpdate();
      });
    },
    openCustomRecurrenceForm() {
      let customEventRecurrence = null;
      const eventRecurrence = this.event && this.event.recurrence || this.event.parent && this.event.parent.recurrence;
      if (eventRecurrence) {
        customEventRecurrence = JSON.parse(JSON.stringify(eventRecurrence));
      } else {
        customEventRecurrence = {
          frequency: 'WEEKLY',
          interval: 1,
          byDay: ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'],
        };
      }
      this.$refs.customRecurrentEventDrawer.open(customEventRecurrence);
    },
  }
};
</script>