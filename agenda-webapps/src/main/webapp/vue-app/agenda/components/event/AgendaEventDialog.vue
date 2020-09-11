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
        if (this.isForm) {
          setTimeout(() => this.$refs.eventForm.reset(), 200);
        }
      } else {
        setTimeout(() => $('body').removeClass('hide-scroll'), 200);
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-form', event => {
      this.isForm = true;
      this.open(event);
    });
    this.$root.$on('agenda-open-event-details', event => {
      this.isForm = false;
      this.open(event);
    });
    this.$root.$on('agenda-event-deleted', this.close);
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
      if (!event.recurrence) {
        event.recurrence = {};
      }
      this.event = event;
      this.$nextTick().then(() => {
        if (this.$refs.eventForm) {
          this.$refs.eventForm.reset();
        }
      });
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