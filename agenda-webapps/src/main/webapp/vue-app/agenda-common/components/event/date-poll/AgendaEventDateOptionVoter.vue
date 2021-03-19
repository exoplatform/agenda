<template>
  <tr>
    <th :class="!voter.profile && 'no-vote' || ''" class="event-date-options-voter-cell justify-center">
      <v-card
        class="d-flex fill-height border-box-sizing mr-3"
        flat>
        <div
          class="my-auto text-center text-truncate pa-3">
          <exo-space-avatar
            v-if="voter.space"
            :space="voter.space"
            :labels="labels"
            class="d-inline-block" />
          <exo-user-avatar
            v-else-if="voter.profile"
            :username="voter.profile.username"
            :fullname="voter.profile.fullname"
            :title="voter.profile.fullname"
            :labels="labels"
            class="d-inline-block date-poll-participant" />
        </div>
        <v-icon
          v-if="isCreator"
          :title="$t('agenda.eventCreator')"
          :size="16"
          class="mb-2">
          fa-crown
        </v-icon>
        <v-btn
          v-if="isCurrentUser"
          :title="$t('agenda.changeVote')"
          :class="isVoting && 'hide-edit-vote'"
          icon
          transparent
          class="ma-auto border-box-sizing"
          @click="$root.$emit('agenda-date-poll-change-vote')">
          <i class="uiIconEditInfo uiIcon16x16 darkGreyIcon pb-2"></i>
        </v-btn>
      </v-card>
    </th>
    <td
      v-for="(dateOptionVote, index) in voter.dateOptionVotes"
      :key="`${index}_${dateOptionVote}`"
      :class="`${selectedDateIndex === index && 'event-date-option-cell-selected' || ''} ${!voter.profile && 'disabled' || ''}`"
      class="event-date-options-voter-cell">
      <v-card
        class="d-flex fill-height text-center border-box-sizing"
        flat>
        <v-card-text class="d-flex ma-auto text-center">
          <agenda-event-date-option-vote
            v-if="voter.profile"
            :voted-date-polls="votedDatePolls"
            :voter="voter"
            :date-option="dateOptions[index]"
            :vote="dateOptionVote"
            :is-voting="isVoting"
            class="flex-grow-0 mx-auto"
            @change="changeVote(index, $event)" />
        </v-card-text>
      </v-card>
    </td>
  </tr>
</template>
<script>
export default {
  props: {
    dateOptions: {
      type: Object,
      default: () => null
    },
    votedDatePolls: {
      type: Array,
      default: () => null
    },
    voter: {
      type: Object,
      default: () => null
    },
    isVoting: {
      type: Boolean,
      default: false,
    },
    selectedDateIndex: {
      type: Boolean,
      default: false,
    },
    eventCreatorId: {
      type: Number,
      default: () => null
    },
  },
  computed: {
    isCurrentUser() {
      return this.voter && this.voter.isCurrentUser;
    },
    isCreator() {
      return this.voter && Number(this.voter.id) === Number(this.eventCreatorId);
    },
    labels() {
      return {
        CancelRequest: this.$t('profile.CancelRequest'),
        Confirm: this.$t('profile.Confirm'),
        Connect: this.$t('profile.Connect'),
        Ignore: this.$t('profile.Ignore'),
        RemoveConnection: this.$t('profile.RemoveConnection'),
        StatusTitle: this.$t('profile.StatusTitle'),
        join: this.$t('space.join'),
        leave: this.$t('space.leave'),
        members: this.$t('space.members'),
      };
    },
  },
  methods: {
    changeVote(index, vote) {
      this.voter.dateOptionVotes[index] = vote;
      this.$forceUpdate();
      this.$emit('changed');
    },
  },
};
</script>