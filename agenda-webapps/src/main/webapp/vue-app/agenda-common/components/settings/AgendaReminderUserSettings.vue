<template>
  <v-list
    class="flex-grow-1 d-flex flex-column pa-0"
    dense>
    <agenda-reminder-user-setting-item
      v-for="(reminder, index) in reminders"
      :key="index"
      :reminder="reminder"
      @remove="removeReminder(reminder)" />
    <v-list-item v-if="canAddReminder" class="pl-0 my-auto">
      <a class="text-subtitle-1 font-weight-regular add-notification-link" @click="addReminder">
        {{ $t('agenda.addReminder') }}
      </a>
    </v-list-item>
  </v-list>
</template>

<script>
const MAX_REMINDERS = 5;

export default {
  props: {
    reminders: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    canAddReminder() {
      return this.reminders.length < MAX_REMINDERS;
    },
  },
  methods: {
    addReminder() {
      this.reminders.push({before: 0, beforePeriodType: 'MINUTE'});
    },
    removeReminder(reminder) {
      this.reminders.splice(this.reminders.indexOf(reminder), 1);
    },
  },
};
</script>