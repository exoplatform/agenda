<template>
  <v-dialog
    v-model="dialog"
    fullscreen
    hide-overlay>
    <agenda-event-form
      ref="eventForm"
      :event="event"
      :weekdays="weekdays"
      class="fill-height event-form"
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
  },
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
      if (event) {
        event = Object.assign({}, event);
      } else {
        event = {};
      }
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
      if (this.$refs.eventForm) {
        this.$nextTick().then(this.$refs.eventForm.reset);
      }
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