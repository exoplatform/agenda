<template>
  <v-dialog
    v-model="dialog"
    fullscreen
    hide-overlay>
    <v-card>
      <agenda-event-form
        :event="event"
        @close="close"
        @saved="saved" />
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  data () {
    return {
      dialog: false,
      event: null,
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
    this.$root.$on('agenda-event-form', event => this.open(event));
  },
  methods: {
    open(event) {
      this.dialog = true;
      event = event || {};
      if (!event.calendar) {
        event.calendar = {};
      }
      if (!event.calendar.owner) {
        event.calendar.owner = {};
      }
      if (!event.reminders) {
        event.reminders = [];
      }
      if (!event.attachments) {
        event.attachments = [];
      }
      if (!event.attendees) {
        event.attendees = [];
      }
      this.event = event;
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