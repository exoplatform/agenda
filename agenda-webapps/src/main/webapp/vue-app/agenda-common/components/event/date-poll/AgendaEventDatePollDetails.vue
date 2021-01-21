<template>
  <v-card
    v-if="event"
    :loading="loading"
    flat
    class="event-details event-poll-details d-flex flex-column">
    <v-btn
      :title="$t('agenda.button.close')"
      class="event-poll-details-close"
      absolute
      right
      icon
      @click="$emit('close')">
      <v-icon>close</v-icon>
    </v-btn>
    <div :class="displayHasVotedInfo && 'pb-2' || 'pb-6'" class="d-flex flex-row px-6 pt-8">
      <v-btn
        v-if="isCreator"
        :title="$t('agenda.changeSuggestedLabel')"
        class="ml-2 mt-3"
        x-large
        absolute
        left
        icon
        @click="$root.$emit('agenda-event-form', event, true)">
        <i class="uiIcon uiIconDatePicker darkGreyIcon uiIcon32x32"></i>
      </v-btn>
      <div class="flex-grow-1 mx-8">
        <v-row class="event-details-header d-flex align-center flex-nowrap text-center col-12">
          <v-col :title="event.summary" class="event-title title text-truncate col-auto pl-4 py-0 mx-auto">
            {{ event.summary }}
          </v-col>
        </v-row>
        <v-row class="justify-center">
          <div class="flex-grow-0 px-0 my-auto">
            {{ $t('agenda.label.createdBy', {0: creatorFullName}) }},
          </div>
          <div class="flex-grow-0 px-0 ml-1 mr-2 my-auto">
            {{ $t('agenda.label.in') }}
          </div>
          <div class="flex-grow-0 d-flex px-0 my-auto">
            <exo-space-avatar
              :space="ownerProfile"
              :size="24"
              :labels="labels" />
          </div>
          <div class="d-flex flex-grow-0 px-0 ml-2 my-auto">
            {{ $t('agenda.label.onDate') }}
            <date-format
              :value="event.created"
              :format="fullDateFormat"
              class="ml-1" />
          </div>
        </v-row>
        <v-row
          v-if="displayHasVotedInfo"
          align="center"
          justify="center">
          <v-alert type="info" class="mb-0">
            {{ $t('agenda.youAlreadyVoted') }}
          </v-alert>
        </v-row>
      </div>
      <template v-if="hasVoted">
        <v-btn
          v-if="isVoting"
          class="btn mr-2 mt-3"
          absolute
          right
          @click="isVoting = false">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          v-else
          class="btn mr-2 mt-3"
          absolute
          right
          @click="isVoting = true">
          <i class="uiIcon uiIconStatistics darkGreyIcon uiIcon16x16 mt-3 mr-2"></i>
          {{ $t('agenda.button.voteAgain') }}
        </v-btn>
      </template>
    </div>
    <v-card
      class="mx-6 mb-8 event-date-options-table"
      outlined
      tile>
      <v-row no-gutters>
        <v-col class="justify-center flex-grow-1" cols="auto">
          <v-card
            class="d-flex fill-height border-box-sizing event-date-options-cell"
            outlined
            tile>
            <v-card-title class="ma-auto text-center">
              {{ $t('agenda.participants') }}
            </v-card-title>
          </v-card>
        </v-col>
        <agenda-event-date-option-period
          v-for="(dateOption, index) in dateOptions"
          :key="index"
          :date-option="dateOption"
          :can-select="canSelectDate"
          :selected="selectedDateOptionIndex === index"
          @select="selectDate(index)" />
      </v-row>
      <v-row no-gutters>
        <v-col class="justify-center flex-grow-1" cols="auto">
          <v-card
            class="d-flex fill-height border-box-sizing event-date-options-cell"
            outlined
            tile>
            <v-card-text class="ma-auto text-center">
              {{ votedAttendeesCount }}
              /
              {{ attendeesCount }}
              {{ $t('agenda.participants') }}
            </v-card-text>
          </v-card>
        </v-col>
        <v-col
          v-for="(dateOption, index) in dateOptions"
          :key="index"
          :class="selectedDateOptionIndex === index && 'event-date-option-cell-selected' || ''"
          cols="2">
          <v-card
            class="d-flex fill-height text-center border-box-sizing event-date-options-cell"
            outlined
            tile>
            <v-card-text class="ma-auto text-center">
              {{ dateOption.voters && dateOption.voters.length || 0 }}
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
      <template v-if="voters">
        <agenda-event-date-option-voter
          v-for="(voter, index) in voters"
          :key="index"
          :voter="voter"
          :date-options="dateOptions"
          :selected-date-index="selectedDateOptionIndex"
          :is-voting="isVoting"
          @changed="enableVoteButton" />
      </template>
    </v-card>
    <v-row
      v-if="isAttendee"
      no-gutters
      class="mx-6 mb-6">
      <v-col class="d-flex">
        <v-btn
          v-if="!hasVoted || isVoting"
          :loading="sendingVotes"
          :disabled="sendingVotes || disableVoteButton"
          class="btn btn-primary ml-auto"
          @click="sendVotes">
          {{ $t('agenda.button.vote') }}
        </v-btn>
        <v-btn
          v-else
          v-show="showCreateButton"
          :loading="creatingEvent"
          :disabled="creatingEvent"
          class="btn btn-primary ml-auto"
          @click="createEvent">
          {{ $t('agenda.button.createEvent') }}
        </v-btn>
      </v-col>
    </v-row>
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
    showCreateButton() {
      return this.isCreator && this.selectedDateOptionIndex > -1;
    },
    displayHasVotedInfo() {
      return this.hasVoted && !this.isVoting;
    },
    creatorFullName() {
      return this.event && this.event.creator && this.event.creator.profile && this.event.creator.profile.fullname || '';
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
      const promises = [];
      this.sendingVotes = true;
      for(const index in this.dateOptions) {
        const dateOptionId = this.dateOptions[index].id;
        if (this.currentUserVotes.dateOptionVotes[index]) {
          promises.push(this.$eventService.voteEventDate(eventId, dateOptionId));
        } else {
          promises.push(this.$eventService.dismissEventDate(eventId, dateOptionId));
        }
      }
      return Promise.all(promises)
        .finally(() => this.$emit('refresh-event'));
    },
  },
};
</script>