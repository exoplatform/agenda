<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <v-list>
      <v-list-item-group
        v-model="selected"
        color="primary">
        <v-list-item
          v-for="(dateOption, index) in dateOptions"
          :key="index">
          <agenda-event-date-option-vote
            class="my-auto"
            :date-option="dateOption"
            :vote="vote"
            :disabled="disabled"
            :voter="voter" />
          <agenda-event-date-option-period-mobile
            :date-option="dateOption"
            class="text--primary my-auto" />
          <v-list-item-content
            class="text--primary my-auto"
            @click="$root.$emit('selected-date-option',dateOption)">
            <exo-user-avatars-list
              :users="dateOption.voters"
              :max="2"
              @open-detail="$root.$emit('agenda-display-voters', votersAcceptedDatePoll)" />          
          </v-list-item-content>
          <div class="my-auto">
            <v-btn
              :title="$t('agenda.finalDate')"
              icon
              right
              fab
              x-small>
              <v-icon color="#f8b441">fa-trophy</v-icon>
            </v-btn>
          </div>
        </v-list-item>
      </v-list-item-group>
    </v-list>
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