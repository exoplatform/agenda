<template>
  <exo-user-avatars-list
    :users="votersAcceptedDatePoll"
    :max="1"
    @open-detail="$root.$emit('agenda-display-voters', votersAcceptedDatePoll)" />
</template>

<script>
export default {
  props: {
    voters: {
      type: Array,
      default: () => null,
    },
    dateOption: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    votersAcceptedDatePoll: []
  }),
  created() {
    this.computeVotersAcceptedDatePoll(this.dateOption);
  },
  methods: {
    computeVotersAcceptedDatePoll(dateOption) {
      dateOption.voters.forEach(voterDateOption => {
        this.voters.forEach(voter => {
          if (Number(voter.id) === voterDateOption) {
            this.votersAcceptedDatePoll.push(voter);
          }
        });
      });
    }
  }
};
</script>
