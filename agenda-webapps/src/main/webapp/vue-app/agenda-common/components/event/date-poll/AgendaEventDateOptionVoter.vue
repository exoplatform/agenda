<template>
  <v-row :align="align" no-gutters>
    <v-col class="justify-center flex-grow-1" cols="auto">
      <v-card
        :class="!voter.profile && 'no-vote' || ''"
        class="d-flex fill-height border-box-sizing event-date-options-voter-cell"
        outlined
        tile>
        <div
          :title="voterFullName"
          class="ma-auto text-center text-truncate">
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
            class="d-inline-block" />
        </div>
      </v-card>
    </v-col>
    <v-col
      v-for="(dateOptionVote, index) in voter.dateOptionVotes"
      :key="index"
      :class="selectedDateIndex === index && 'event-date-option-cell-selected' || ''"
      cols="2">
      <v-card
        :class="!voter.profile && 'disabled' || ''"
        class="d-flex fill-height text-center border-box-sizing event-date-options-voter-cell"
        outlined
        tile>
        <v-card-text class="d-flex ma-auto text-center">
          <agenda-event-date-option-vote
            v-if="voter.profile"
            :voter="voter"
            :date-option="dateOptions[index]"
            :vote="dateOptionVote"
            :disabled="!isVoting"
            class="flex-grow-0 mx-auto"
            @change="changeVote(index, $event)" />
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>
<script>
export default {
  props: {
    dateOptions: {
      type: Object,
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
  },
  computed: {
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