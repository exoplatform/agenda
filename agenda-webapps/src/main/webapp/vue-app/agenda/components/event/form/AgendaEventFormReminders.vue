<template>
  <v-list class="flex-grow-1 d-flex flex-column pa-0">
    <v-list-item
      v-for="(reminder, index) in event.reminders"
      :key="index"
      class="pl-0">
      <label class="subtitle-1 float-left mr-4">{{ $t('agenda.label.notifyMe') }}</label>
      <input
        ref="timeNotification"
        v-model="reminder.before"
        type="number"
        name="timeNotification"
        class="subtitle-1 ignore-vuetify-classes my-3 time-notification"
        required
        numeric>
      <select
        v-model="reminder.beforePeriodType"
        class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes notification-date-option">
        <option value="MINUTE">{{ $t('agenda.option.minutes') }}</option>
        <option value="HOUR">{{ $t('agenda.option.hours') }}</option>
        <option value="DAY">{{ $t('agenda.option.days') }}</option>
        <option value="WEEK">{{ $t('agenda.option.weeks') }}</option>
      </select>
      <span class="subtitle-1 ml-4">{{ $t('agenda.label.beforeStart') }}</span>
      <v-btn
        color="grey"
        icon
        dark
        @click="removeReminder(index)">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </v-list-item>
    <v-list-item class="pl-0 my-auto">
      <a class="text-subtitle-1 font-weight-regular add-notification-link" @click="addReminder">{{ $t('agenda.addReminder') }}</a>
    </v-list-item>
  </v-list>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  methods:{
    addReminder() {
      this.event.reminders.push({
        before: 10,
        beforePeriodType: 'MINUTE',
      });
    },
    removeReminder(index) {
      this.event.reminders.splice(index, 1);
    },
  }
};
</script>