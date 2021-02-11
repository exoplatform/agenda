<template>
  <div class="text-no-wrap">
    <v-btn
      class="btn btn-primary"
      @click="openNewEventForm">
      <v-icon dark>
        mdi-plus
      </v-icon>
      <span class="ml-2 d-none d-lg-inline">
        {{ $t('agenda.button.addEvent') }}
      </span>
    </v-btn>
    <agenda-pending-invitation-badge :current-space="currentSpace" />
  </div>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
  },
  methods: {
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
  },
};
</script>
