<template>
  <v-dialog
    v-model="dialog"
    fullscreen
    hide-overlay>
    <agenda-event-form
      v-if="isForm"
      ref="eventForm"
      :event="event"
      :weekdays="weekdays"
      :current-space="currentSpace"
      :working-time="workingTime"
      class="fill-height event-form"
      @close="close"
      @saved="saved" />
    <agenda-event-details
      v-else
      ref="eventDetails"
      :event="event"
      :weekdays="weekdays"
      @close="close"
      @saved="saved" />
  </v-dialog>
</template>

<script>
export default {
  props: {
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
    currentSpace: {
      type: Object,
      default: () => null
    },
  },
  data () {
    return {
      dialog: false,
      event: null,
      isForm: false
    };
  },
  watch: {
    dialog() {
      if (this.dialog) {
        $('body').addClass('hide-scroll');
      } else {
        setTimeout(() => $('body').removeClass('hide-scroll'), 200);
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-form', agendaEvent => {
      this.isForm = true;
      this.open(agendaEvent);
      this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', agendaEvent));
    });
    this.$root.$on('agenda-event-details', agendaEvent => {
      this.isForm = false;

      const eventId = agendaEvent.id ? agendaEvent.id : agendaEvent.parent && agendaEvent.parent.id;
      this.$eventService.getEventById(eventId, 'all')
        .then(event => {
          agendaEvent.attendees = event.attendees;
          agendaEvent.conferences = event.conferences;
          agendaEvent.attachments = event.attachments;
          agendaEvent.reminders = event.reminders;

          this.open(agendaEvent);

          this.$nextTick().then(() => this.$root.$emit('agenda-event-details-opened', agendaEvent));
        });
    });
    this.$root.$on('agenda-event-deleted', this.close);
  },
  methods: {
    open(agendaEvent) {
      this.dialog = true;
      if (!agendaEvent) {
        agendaEvent = {};
      }
      if (!agendaEvent.calendar) {
        agendaEvent.calendar = {};
      }
      if (!agendaEvent.calendar.owner) {
        agendaEvent.calendar.owner = {};
      }
      if (!agendaEvent.reminders) {
        agendaEvent.reminders = [];
      }
      if (!agendaEvent.attachments) {
        agendaEvent.attachments = [];
      }
      if (!agendaEvent.attendees) {
        agendaEvent.attendees = [];
      }
      this.event = agendaEvent;
    },
    close() {
      this.dialog = false;
    },
    saved() {
      this.$root.$emit('refresh');
      this.close();
    },
  },
};
</script>