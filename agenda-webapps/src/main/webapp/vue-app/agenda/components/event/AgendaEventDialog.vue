<template>
  <v-dialog
    v-model="dialog"
    persistent
    fullscreen
    hide-overlay
    @keydown.esc="close">
    <template v-if="event">
      <template v-if="isForm">
        <agenda-event-mobile-form
          v-if="isMobile"
          ref="eventForm"
          :event="event"
          :current-space="currentSpace"
          class="fill-height event-form"
          @close="close"
          @saved="saved" />
        <agenda-event-form
          v-else
          ref="eventForm"
          :event="event"
          :weekdays="weekdays"
          :current-space="currentSpace"
          :working-time="workingTime"
          class="fill-height event-form"
          @close="close"
          @saved="saved" />
      </template>
      <agenda-event-details
        v-else
        ref="eventDetails"
        :event="event"
        :weekdays="weekdays"
        @close="close" />
    </template>
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
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        if (!this.isMobile) {
          $('body').addClass('hide-scroll');
        }
      } else {
        if (!this.isMobile) {
          setTimeout(() => $('body').removeClass('hide-scroll'), 200);
        }
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
        this.openEventDetails(eventId);
      }
    }
    this.$root.$on('agenda-event-form', agendaEvent => {
      const eventId = agendaEvent.id ? agendaEvent.id : agendaEvent.parent && agendaEvent.parent.id;
      if (eventId) {
        const occurrenceEvent = !agendaEvent.id && agendaEvent || null;
        this.openEventForm(eventId, occurrenceEvent);
      } else {
        this.isForm = true;
        this.openDialog(agendaEvent);
        this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', agendaEvent));
      }
    });
    this.$root.$on('agenda-event-details', agendaEvent => {
      const eventId = agendaEvent.id ? agendaEvent.id : agendaEvent.parent && agendaEvent.parent.id;
      const occurrenceEvent = !agendaEvent.id && agendaEvent || null;
      this.openEventDetails(eventId, occurrenceEvent);
    });
    this.$root.$on('agenda-event-deleted', this.close);
    this.$root.$on('agenda-event-saved', this.close);
  },
  methods: {
    openEventForm(eventId, occurrenceEvent) {
      this.isForm = true;
      this.openEventById(eventId, occurrenceEvent);
    },
    openEventDetails(eventId, occurrenceEvent) {
      this.isForm = false;
      this.openEventById(eventId, occurrenceEvent);
    },
    openEventById(eventId, occurrenceEvent) {
      if (eventId) {
        this.$eventService.getEventById(eventId, 'all')
          .then(event => {
            if (occurrenceEvent) {
              event.id = occurrenceEvent.id;
              event.start = occurrenceEvent.start;
              event.end = occurrenceEvent.end;
              event.occurrence = occurrenceEvent.occurrence;
              event.parent = occurrenceEvent.parent;
            }
            event.startDate = this.$agendaUtils.toDate(event.start);
            event.endDate = this.$agendaUtils.toDate(event.end);
  
            this.openDialog(event);
            if (this.isForm) {
              this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', event));
            } else {
              this.$nextTick().then(() => this.$root.$emit('agenda-event-details-opened', event));
            }
          });
      }
    },
    openDialog(agendaEvent) {
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
      window.history.replaceState('', window.document.title, window.location.pathname);
    },
  },
};
</script>