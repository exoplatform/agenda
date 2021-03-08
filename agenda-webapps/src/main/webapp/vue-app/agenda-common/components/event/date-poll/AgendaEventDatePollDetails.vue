<template>
  <v-card
    v-if="event"
    :loading="loading"
    flat
    class="event-details event-poll-details d-flex flex-column">
    <div :class="displayHasVotedInfo && 'pb-2' || 'pb-6'" class="px-6 pt-8">
      <div class="flex-grow-1 mx-8">
        <v-row class="event-details-header d-flex align-center flex-nowrap text-center col-12">
          <v-col class="font-italic subtitle-1 col-auto pl-4 py-0 mx-auto justify-center mb-10">
            {{ event.description }}
          </v-col>
        </v-row>
      </div>
    </div>
    <div class="d-flex flex-column px-6 pb-8">
      <table
        v-if="!isMobile"
        id="event-date-options-table"
        description="Event date options table"
        class="event-date-options-table mx-auto">
        <tr>
          <th id="participantsTitle" class="event-date-options-cell justify-center">
            <v-card
              class="d-flex fill-height border-box-sizing"
              flat>
              <v-card-title class="ma-auto text-no-wrap text-center">
                {{ $t('agenda.participants') }}
              </v-card-title>
            </v-card>
          </th>
          <agenda-event-date-option-period
            v-for="(dateOption, index) in dateOptions"
            :key="index"
            :date-option="dateOption"
            :can-select="canSelectDate"
            :selected="selectedDateOptionIndex === index"
            @select="selectDate(index)" />
        </tr>
        <tr>
          <th id="participantsCount" class="event-date-options-cell justify-center">
            <v-card
              class="d-flex fill-height border-box-sizing"
              flat>
              <v-card-text class="ma-auto text-center text-no-wrap">
                {{ votedAttendeesCount }}
                /
                {{ attendeesCount }}
                {{ $t('agenda.participants') }}
              </v-card-text>
            </v-card>
          </th>
          <th
            v-for="(dateOption, index) in dateOptions"
            :id="`dateOption_${index}`"
            :key="index"
            :class="selectedDateOptionIndex === index && 'event-date-option-cell-selected' || ''"
            class="event-date-options-cell">
            <v-card
              class="d-flex fill-height text-center border-box-sizing"
              flat>
              <v-card-text class="ma-auto text-center">
                {{ dateOption.voters && dateOption.voters.length || 0 }}
              </v-card-text>
            </v-card>
          </th>
        </tr>
        <template v-if="voters">
          <agenda-event-date-option-voter
            v-for="(voter, index) in voters"
            :key="index"
            :voter="voter"
            :date-options="dateOptions"
            :selected-date-index="selectedDateOptionIndex"
            :is-voting="isVoting"
            :event-creator-id="event.creator.id"
            @changed="enableVoteButton"
            @change-vote="isVoting = true" />
        </template>
      </table>
      <agenda-event-date-poll-details-mobile
        v-else
        :event="event"
        :date-options="dateOptions"
        :is-voting="isVoting"
        :is-creator="isCreator"
        :voters="voters" />
    </div>
    <v-row
      v-if="isAttendee"
      no-gutters
      class="mx-6 mb-6">
      <v-col class="d-flex">
        <template v-if="isVoting">
          <v-btn
            :disabled="sendingVotes"
            class="btn ml-auto mr-2"
            @click="isVoting = false">
            {{ $t('agenda.button.cancel') }}
          </v-btn>
          <v-btn
            :loading="sendingVotes"
            :disabled="sendingVotes || disableVoteButton"
            class="btn btn-primary"
            @click="sendVotes">
            {{ $t('agenda.button.vote') }}
          </v-btn>
        </template>
        <v-btn
          v-else-if="isCreator"
          :loading="creatingEvent"
          :disabled="disableCreateButton"
          class="btn btn-primary ml-auto"
          @click="createEvent">
          {{ $t('agenda.button.createEvent') }}
        </v-btn>
      </v-col>
    </v-row>
    <agenda-date-option-conflict-drawer />
  </v-card>
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
      fullDateFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      },
      dateDayFormat: {
        month: 'short',
        day: 'numeric',
      },
      dateTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    isCreator() {
      return this.event && this.event.creator && Number(this.event.creator.id) === this.currentUserId;
    },
    canSelectDate() {
      return this.isCreator && !this.isVoting;
    },
    disableCreateButton() {
      return this.selectedDateOptionIndex < 0;
    },
    displayHasVotedInfo() {
      return this.hasVoted && !this.isVoting;
    },
    owner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
    },
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
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
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
  },
  methods: {
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
    enableVoteButton() {
      this.disableVoteButton = this.hasVoted && this.originalUserVotes.join('') === this.currentUserVotes.dateOptionVotes.join('');
    },
    selectDate(index) {
      if (this.isCreator && !this.isVoting) {
        if (this.selectedDateOptionIndex === index) {
          this.selectedDateOptionIndex = -1;
        } else {
          this.selectedDateOptionIndex = index;
        }
      }
    },
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
    sendVotes() {
      const eventId = this.event.id;
      const acceptedDateOptionIds = [];
      for(const index in this.dateOptions) {
        const dateOptionId = this.dateOptions[index].id;
        if (this.currentUserVotes.dateOptionVotes[index]) {
          acceptedDateOptionIds.push(dateOptionId);
        }
      }
      this.sendingVotes = true;
      this.$eventService.saveEventVotes(eventId, acceptedDateOptionIds)
        .finally(() => this.$emit('refresh-event'));
    },
  },
};
</script>