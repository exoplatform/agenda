<template>
  <v-dialog
    ref="agendaDialog"
    v-model="dialog"
    fullscreen
    hide-overlay
  >
    <v-card>
      <agenda-event-form :event="event" @close="close" />
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
  methods: {
    open(event) {
      this.dialog = true;
      this.event = event || {};
    },
    close() {
      this.dialog = false;
    },
  },
};
</script>