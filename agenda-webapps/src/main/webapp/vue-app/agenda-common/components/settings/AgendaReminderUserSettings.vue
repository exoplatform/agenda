<template>
  <div>
    <div class="d-flex mt-6 mb-4 ">
      <div class="text-header ma-auto ">{{ $t('agenda.label.defaultReminders') }}</div>
      <v-spacer />
      <v-btn
        v-if="canAddReminder"
        :title="$t('agenda.addReminder')"
        class="btn btn-primary ma-auto px-0 me-2"
        max-height="34"
        min-width="34"
        @click="addReminder">
        <v-icon size="18">
          fa-plus
        </v-icon>
      </v-btn>
    </div>
    <v-list
      class="flex-grow-1 d-flex flex-column pa-0"
      dense>
      <v-list-item
        v-if="!reminders || !reminders.length"
        class="px-0 reminder-list-item"
        dense>
        <label class="text-subtitle mx-auto">
          {{ $t('agenda.noRemindersYet') }}
        </label>
      </v-list-item>
      <agenda-reminder-user-setting-item
        v-for="(reminder, index) in reminders"
        :key="index"
        :reminder="reminder"
        @remove="removeReminder(reminder)" />
    </v-list>
  </div>
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