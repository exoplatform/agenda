<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <exo-user-avatars-list
    :users="votersAcceptedDatePoll"
    :max="2"
    :icon-size="28"
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
    this.computeVoters(this.dateOption);
  },
  methods: {
    computeVoters(dateOption) {
      dateOption.voters.forEach(voter => {
        this.computeVoterFromIdentity(voter);
      });
    },
    computeVoterFromIdentity(voter) {
      const attendeesAcceptedVote = [];
      return this.$identityService.getIdentityById(Number(voter))
        .then(identity => {
          attendeesAcceptedVote.username = identity.profile.username;
          attendeesAcceptedVote.fullname = identity.profile.fullname;
          this.votersAcceptedDatePoll.push(attendeesAcceptedVote);
        });
    }
  }
};
</script>