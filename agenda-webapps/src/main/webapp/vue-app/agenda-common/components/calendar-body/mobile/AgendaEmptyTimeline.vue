<template>
  <v-sheet class="d-flex flex-column justify-center align-center fill-height z-index-one pa-5" max-height="100%">
    <v-icon
      color="secondary"
      size="60"
      class="mb-2">
      fas fa-calendar
    </v-icon>
    <div class="d-flex flex-grow-1 my-3 align-center justify-center">
      <v-btn
        v-if="displayAddEvent"
        class="btn btn-primary"
        outlined
        @click="openEventForm">
        {{ $t('agenda.title.addEvent') }}
      </v-btn>
    </div>
  </v-sheet>
</template>
<script>
export default {
  props: {
    canCreateEvent: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    displayAddEvent() {
      return this.canCreateEvent &&  this.$root.timelineSettings.displayAddEvent !== false;
    },
  },  

  methods: {
    openEventForm() {
      this.$root.$emit('agenda-event-quick-form', {
        summary: '',
        startDate: new Date(),
        endDate: new Date(),
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