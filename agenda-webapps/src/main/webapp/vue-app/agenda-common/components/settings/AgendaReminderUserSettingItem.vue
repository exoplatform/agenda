<template>
  <v-list-item class="px-0 reminder-list-item" dense>
    <input
      ref="beforeInput"
      v-model.number="reminder.before"
      type="number"
      class="reminder-time ignore-vuetify-classes my-auto"
      min="0"
      required
      numeric>
    <select
      ref="periodTypeInput"
      v-model="reminder.beforePeriodType"
      class="reminder-period-type width-auto my-auto ml-4 pr-2 ignore-vuetify-classes"
      required>
      <option value="MINUTE">{{ $t('agenda.option.minutes') }}</option>
      <option value="HOUR">{{ $t('agenda.option.hours') }}</option>
      <option value="DAY">{{ $t('agenda.option.days') }}</option>
    </select>
    <span class="ml-4">
      {{ $t('agenda.label.beforeStart') }}
    </span>
    <v-btn
      color="grey"
      icon
      dark
      class="ml-auto"
      @click="$emit('remove')">
      <v-icon>
        mdi-close
      </v-icon>
    </v-btn>
  </v-list-item>
</template>

<script>
const MAX_DAYS = 1;
const MAX_HOURS = MAX_DAYS * 24;
const MAX_MINUTES = MAX_HOURS * 60;

export default {
  props: {
    reminder: {
      type: Object,
      default: () => ({}),
    },
  },
  computed: {
    before() {
      return this.reminder && this.reminder.before;
    },
    periodType() {
      return this.reminder && this.reminder.beforePeriodType;
    },
  },
  watch: {
    before() {
      this.validateForm();
    },
    periodType() {
      this.validateForm();
    },
  },
  methods: {
    validateForm() {
      try {
        if (this.before < 0) {
          this.$refs.beforeInput.setCustomValidity(this.$t('agenda.warning.reminder.noNegativeNumber'));
          return;
        }
        if (this.periodType === 'MINUTE') {
          if (this.before > MAX_MINUTES) {
            this.$refs.beforeInput.setCustomValidity(this.$t('agenda.warning.reminder.maxMinutes', {0: MAX_MINUTES}));
            return;
          }
        } else if (this.periodType === 'HOUR') {
          if (this.before > MAX_HOURS) {
            this.$refs.beforeInput.setCustomValidity(this.$t('agenda.warning.reminder.maxHours', {0: MAX_HOURS}));
            return;
          }
        } else if (this.periodType === 'DAY') {
          if (this.before > MAX_DAYS) {
            this.$refs.beforeInput.setCustomValidity(this.$t('agenda.warning.reminder.maxDays', {0: MAX_DAYS}));
            return;
          }
        }
  
        this.$refs.beforeInput.setCustomValidity('');
      } finally {
        this.$refs.beforeInput.reportValidity();
      }
    },
  },
};
</script>