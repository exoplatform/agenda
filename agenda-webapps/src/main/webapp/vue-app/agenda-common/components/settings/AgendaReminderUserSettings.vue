<template>
  <v-list
    class="flex-grow-1 d-flex flex-column pa-0"
    dense>
    <label class="subtitle-1 float-left mr-4">
      {{ $t('agenda.label.defaultReminders') }}
    </label>
    <agenda-reminder-user-setting-item
      v-for="(reminder, index) in reminders"
      :key="index"
      :reminder="reminder"
      @remove="removeReminder(remove)" />
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
  data: () => ({
    reminders: [],
  }),
  computed: {
    canAddReminder() {
      return this.reminders.length < MAX_REMINDERS;
    },
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      return this.$eventService.getUserReminderSettings()
        .then(reminders => this.reminders = reminders || []);
    },
    addReminder() {
      this.reminders.push({before: 0, periodType: 'MINUTE'});
    },
    removeReminder(reminder) {
      this.reminders.splice(this.reminders.indexOf(reminder), 1);
    },
    save() {
      return this.$eventService.saveUserReminderSettings(this.reminders);
    },
  },
};
</script>