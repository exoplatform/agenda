<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <agenda-event-date-poll-item-mobile
      :date-options="dateOptions"
      :current-user-votes="currentUserVotes"
      :event="event"
      :voters="voters" />
    <agenda-date-poll-participants-drawer-mobile :event-title="event.summary" />
    <v-btn
      :loading="creatingEvent"
      :disabled="disableCreateButton"
      class="btn btn-primary float-right mr-2"
      @click="createEvent">
      {{ $t('agenda.button.createEvent') }}
    </v-btn>
  </v-flex>
</template>

<script>
export default {
  props: {
    dateOptions: {
      type: Object,
      default: () => null
    },
    canSelectDate: {
      type: Boolean,
      default: false,
    },
    voters: {
      type: Array,
      default: () => null,
    },
    event: {
      type: Object,
      default: () => null
    },
    currentUserVotes: {
      type: Object,
      default: () => null
    },
    selectedDateOptionIndex: {
      type: Number,
      default: -1
    },
  },
  data: () => ({
    vote: false,
    voter: null,
    disabled: false,
    creatingEvent: false,
  }),
  computed: {
    disableCreateButton() {
      return this.selectedDateOptionIndex < 0;
    },
  },
  methods: {
    createEvent() {
      const dateOption = this.dateOptions[this.selectedDateOptionIndex];
      this.creatingEvent = true;
      return this.$eventService.selectEventDate(dateOption.eventId, dateOption.id)
        .then(() => {
          this.$root.$emit('agenda-refresh');
          window.setTimeout(() => this.$root.$emit('agenda-event-details', this.event), 200);
        })
        .finally(() => {
          window.setTimeout(() => {
            this.creatingEvent = false;
          }, 200);
        });
    },
  }
};
</script>