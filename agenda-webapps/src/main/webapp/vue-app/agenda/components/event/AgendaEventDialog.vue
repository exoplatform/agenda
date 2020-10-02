<template>
  <v-dialog
    v-model="dialog"
    persistent
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
      @close="close" />
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
      isForm: false,
    };
  },
  watch: {
    dialog() {
      if (this.dialog) {
        $('body').addClass('hide-scroll');
      } else {
        setTimeout(() => $('body').removeClass('hide-scroll'), 200);
        this.event = null;
      }
    },
  },
  created() {
    const search = document.location.search.substring(1);
    if(search) {
      const parameters = JSON.parse(
        `{"${decodeURI(search)
          .replace(/"/g, '\\"')
          .replace(/&/g, '","')
          .replace(/=/g, '":"')}"}`
      );
      const eventId = parameters.eventId;
      if (eventId) {
        this.openEventById(eventId);
      }
    }
    this.$root.$on('agenda-event-form', agendaEvent => {
      this.isForm = true;

      agendaEvent = JSON.parse(JSON.stringify(agendaEvent));
      const eventId = agendaEvent.id ? agendaEvent.id : agendaEvent.parent && agendaEvent.parent.id;
      if (eventId) {
        this.$eventService.getEventById(eventId, 'all')
          .then(event => {
            agendaEvent.attendees = event.attendees;
            agendaEvent.conferences = event.conferences;
            agendaEvent.attachments = event.attachments;
            agendaEvent.reminders = event.reminders;

            agendaEvent.startDate = this.$agendaUtils.toDate(agendaEvent.start);
            agendaEvent.endDate = this.$agendaUtils.toDate(agendaEvent.end);

            this.open(agendaEvent);
            this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', agendaEvent));
          });
      } else {
        this.open(agendaEvent);
        this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', agendaEvent));
      }
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
    this.$root.$on('agenda-event-saved', this.close);
  },
  methods: {
    openEventById(eventId) {
      this.isForm = false;
      this.$eventService.getEventById(eventId, 'all')
        .then(event => {
          this.open(event);
          this.$nextTick().then(() => this.$root.$emit('agenda-event-details-opened', event));
        });
    },
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
      window.location.replace(window.location.pathname);
    },
  },
};
</script>