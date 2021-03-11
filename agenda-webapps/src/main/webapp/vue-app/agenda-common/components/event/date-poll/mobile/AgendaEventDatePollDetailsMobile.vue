<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <agenda-event-date-poll-item-mobile :date-options="dateOptions" />
    <agenda-date-poll-participants-drawer-mobile :event-title="eventTitle" />
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
    eventTitle: {
      type: String,
      default: () => null
    },
  },
  data: () => ({
    selected: 0,
    vote: false,
    voter: null,
    disabled: false,
  }),
  created() {
    this.initializeCurrentUserVote();
  },
  methods: {
    initializeCurrentUserVote() {
      this.voters.forEach(voter => {
        if(voter.isCurrentUser) {
          this.voter = voter;
          voter.dateOptionVotes.forEach(dateOptionVote => {
            if (dateOptionVote) {
              this.vote = dateOptionVote;
            } else {
              this.vote = false;
            }
          });
        }
      });
    },
  }
};
</script>