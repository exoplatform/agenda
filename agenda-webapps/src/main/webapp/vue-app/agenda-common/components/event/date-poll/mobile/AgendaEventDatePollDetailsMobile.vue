<template>
  <v-flex class="agenda-date-poll-details-mobile" :loading="loading">
    <div class="ml-3 mt-5 mb-3 grey--text text-subtitle-2">{{ $t('agenda.votePreferredDates') }}</div>
    <agenda-date-poll-participants-drawer-mobile :event-title="event.summary" />
    <agenda-date-option-conflict-drawer />
    <agenda-event-date-poll-item-mobile
      :date-options="dateOptions"
      :current-user-votes="currentUserVotes"
      :event="event"
      :voters="voters"
      :is-voting="isVoting"
      @changed="enableVoteButton" />
    <div class="float-right mt-5 mr-2">
      <agenda-date-poll-action-buttons
        :is-voting="isVoting"
        :sending-votes="sendingVotes"
        :current-user-votes="currentUserVotes"
        :date-options="dateOptions"
        :event="event"
        :disable-vote-button="disableVoteButton"
        :voters="voters"
        :selected-date-index="selectedDateOptionIndex" />
    </div>
  </v-flex>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
  },
  data () {
    return {
      voters: null,
      loading: true,
      sendingVotes: false,
      creatingEvent: false,
      selectedDateOptionIndex: -1,
      currentUserAttendee: null,
      isVoting: false,
      hasVoted: false,
      disableVoteButton: true,
      originalUserVotes: null,
      currentUserId: Number(eXo.env.portal.userIdentityId),
    };
  },
  computed: {
    dateOptions() {
      return this.event && this.event.dateOptions || [];
    },
    attendees() {
      return this.event && this.event.attendees || [];
    },
    attendeesCount() {
      return this.attendees.filter(attendee => attendee.identity && attendee.identity.profile).length;
    },
    votedAttendees() {
      const votedAttendees = new Set();
      this.dateOptions.forEach(dateOption => {
        if (dateOption.voters) {
          dateOption.voters.forEach(voter => {
            votedAttendees.add(voter);
          });
        }
      });
      return Array.from(votedAttendees);
    },
    votedAttendeesCount() {
      return this.votedAttendees.length;
    },
    isAttendee() {
      return this.event && this.event.acl && this.event.acl.attendee;
    },
    currentUserVotes() {
      return this.currentUserAttendee && this.currentUserAttendee.identity;
    },
  },
  watch: {
    event() {
      if (this.event) {
        this.computeVoters();
        if (this.isVoting) {
          window.setTimeout(() => {
            this.sendingVotes = false;
            this.hasVoted = true;
            this.isVoting = false;
            this.originalUserVotes = this.currentUserVotes.dateOptionVotes.slice();
            this.enableVoteButton();
          }, 200);
        }
      }
    },
    isVoting() {
      if (this.isVoting) {
        this.selectedDateOptionIndex = -1;
      } else if (this.isCreator) {
        this.preselectDateOption();
      }
      this.enableVoteButton();
    },
    currentUserAttendee() {
      if (this.currentUserAttendee && this.currentUserAttendee.identity) {
        this.originalUserVotes = this.currentUserAttendee.identity.dateOptionVotes.slice();
        this.hasVoted = this.hasVoted || this.currentUserAttendee.response === 'TENTATIVE';
        if (this.currentUserAttendee.response !== 'TENTATIVE') {
          this.isVoting = true;
        }
      }
    },
    dateOptions() {
      if (this.isCreator && this.dateOptions) {
        this.preselectDateOption();
      }
    },
  },
  created() {
    this.computeVoters().finally(() => this.loading = false);
    this.$root.$on('change-vote', ()=> {
      this.isVoting = true;
    });
    this.$root.$on('selected-date-option', (dateOption , selected)=> {
      this.selectedDateOptionIndex = selected;
    });
    this.$root.$on('agenda-date-poll-voted', () => {
      this.isVoting = false;
      this.sendingVotes = false;
    });
  },
  methods: {
    enableVoteButton() {
      this.disableVoteButton = this.hasVoted && this.originalUserVotes.join('') === this.currentUserVotes.dateOptionVotes.join('');
    },
    preselectDateOption() {
      if (this.dateOptions && this.isCreator && this.hasVoted) {
        let selectedDateOption = null;
        let selectedDateOptionIndex = null;
        let maxVotes = 0;
        this.dateOptions.forEach((dateOption, index) => {
          if (dateOption.voters && dateOption.voters.length > maxVotes) {
            selectedDateOption = dateOption;
            selectedDateOptionIndex = index;
            maxVotes = dateOption.voters.length;
          }
        });
        if (selectedDateOption) {
          this.selectedDateOptionIndex = selectedDateOptionIndex;
        }
      }
    },
    computeVoters() {
      if (this.dateOptions) {
        this.dateOptions.sort((dateOption1, dateOption2) => dateOption1.start.localeCompare(dateOption2.start));
      }
      if (this.event.attendees) {
        this.voters = [];
        const attendees = [...this.attendees];
        const isDirectAttendee = attendees.find(attendee => attendee && attendee.identity && Number(attendee.identity.id) === this.currentUserId);
        if (this.isAttendee && !isDirectAttendee) {
          return this.$identityService.getIdentityById(this.currentUserId)
            .then(identity => {
              attendees.push({identity});
              this.computeVotersFromAttendees(attendees);
            });
        } else {
          this.computeVotersFromAttendees(attendees);
        }
      } else {
        this.voters = null;
      }
      return Promise.resolve(null);
    },
    computeVotersFromAttendees(attendees) {
      const voters = [];
      attendees.forEach(attendee => {
        this.computeVoterFromIdentity(voters, attendee);
      });
      voters.sort((voter1, voter2) =>
        Number(voter1.id) === this.currentUserId && -1
          || Number(voter2.id) === this.currentUserId && 1
          || voter1.space && 1
          || voter2.space && -1
          || voter1.fullName.localeCompare(voter2.fullName)
      );
      this.voters = voters;
    },
    computeVoterFromIdentity(voters, attendee) {
      const voter = attendee.identity;
      voter.fullName = voter.profile && voter.profile.fullname || voter.space && voter.space.displayName || '';
      if (Number(voter.id) === this.currentUserId) {
        voter.isCurrentUser = true;
        this.currentUserAttendee = attendee;
      }
      voter.dateOptionVotes = [];
      this.dateOptions.forEach(dateOption => {
        const acceptedVote = dateOption && dateOption.voters && dateOption.voters.indexOf(Number(voter.id)) >= 0 || false;
        voter.dateOptionVotes.push(acceptedVote);
      });
      voters.push(voter);
    },
  },
};
</script>