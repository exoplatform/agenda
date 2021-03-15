<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <agenda-event-date-poll-item-mobile
      :date-options="dateOptions"
      :current-user-votes="currentUserVotes"
      :event="event"
      :voters="voters" />
    <agenda-date-poll-participants-drawer-mobile :event-title="event.summary" />
    <v-btn
      v-if="isCreator"
      :loading="creatingEvent"
      :disabled="disableCreateButton"
      class="btn btn-primary float-right mr-2 mt-5 "
      @click="createEvent">
      {{ $t('agenda.button.createEvent') }}
    </v-btn>
    <agenda-date-option-conflict-drawer />
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
  },
  data: () => ({
    vote: false,
    voter: null,
    disabled: false,
    creatingEvent: false,
    currentUserId: Number(eXo.env.portal.userIdentityId),
    selectedDateOptionIndex: 0,
  }),
  computed: {
    isCreator() {
      return this.event && this.event.creator && Number(this.event.creator.id) === this.currentUserId;
    },
    disableCreateButton() {
      return this.selectedDateOptionIndex < 0;
    },
  },
  created() {
    this.$root.$on('selected-date-option', (dateOption , selected)=> {
      this.dateOption = dateOption;
      this.selectedDateOptionIndex = selected;
    });
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