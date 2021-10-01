<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-card
    v-if="event"
    :loading="loading"
    flat
    class="event-details event-poll-details d-flex flex-column">
    <agenda-event-date-poll-details-mobile
      v-if="isMobile"
      :event="event"
      :voted-date-polls="votedDatePolls"
      :date-options="dateOptions"
      :voters="voters"
      :current-user-votes="currentUserVotes"
      :attendees-count="attendeesCount"
      :voted-attendees-count="votedAttendeesCount"
      :can-select-date="canSelectDate"
      :is-voting="isVoting"
      :selected-date-option-index="selectedDateOptionIndex"
      @selectDate="selectDate($event)"
      @enableVoteButton="enableVoteButton()" />
    <agenda-event-date-poll-details-desktop
      v-else
      :event="event"
      :voted-date-polls="votedDatePolls"
      :date-options="dateOptions"
      :voters="voters"
      :attendees-count="attendeesCount"
      :voted-attendees-count="votedAttendeesCount"
      :can-select-date="canSelectDate"
      :is-voting="isVoting"
      :selected-date-option-index="selectedDateOptionIndex"
      @selectDate="selectDate($event)"
      @enableVoteButton="enableVoteButton()" />
    <v-row
      v-if="isAttendee"
      no-gutters
      class="mx-6 mb-6">
      <v-col class="d-flex justify-end">
        <agenda-date-poll-action-buttons
          :is-voting="isVoting"
          :sending-votes="sendingVotes"
          :current-user-votes="currentUserVotes"
          :date-options="dateOptions"
          :event="event"
          :disable-vote-button="disableVoteButton"
          :voters="voters"
          :selected-date-index="selectedDateOptionIndex" />
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
      loading: false,
      sendingVotes: false,
      selectedDateOptionIndex: -1,
      isVoting: false,
      hasVoted: false,
      votedDatePolls: null,
      disableVoteButton: true,
      originalUserVotes: null,
      currentUserId: Number(eXo.env.portal.userIdentityId),
    };
  },
  computed: {
    isCreator() {
      return this.event && this.event.creator && Number(this.event.creator.id) === this.currentUserId;
    },
    canSelectDate() {
      return this.isCreator && !this.isVoting;
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
    votedAttendeesCount() {
      return this.voters && this.voters.filter(voter => voter.hasVoted).length || 0;
    },
    isAttendee() {
      return this.event && this.event.acl && this.event.acl.attendee;
    },
    currentUserAttendee() {
      return this.attendees.find(attendee => attendee && attendee.identity && Number(attendee.identity.id) === this.currentUserId);
    },
    currentUserVotes() {
      return this.currentUserAttendee && this.currentUserAttendee.identity;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  watch: {
    event() {
      if (this.event) {
        if (this.isVoting) {
          window.setTimeout(() => {
            this.sendingVotes = false;
            this.hasVoted = true;
            this.isVoting = false;
            this.originalUserVotes = this.currentUserVotes.dateOptionVotes.slice();
            this.enableVoteButton();
          }, 200);
        }
        this.voters = this.$datePollUtils.computeVoters(this.event);
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
    voters() {
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
    this.$root.$on('agenda-date-poll-change-vote', ()=> {
      this.isVoting = true;
    });
    this.$root.$on('agenda-date-poll-voted', () => {
      this.$emit('refresh-event');
    });
    this.$root.$on('agenda-date-poll-canceled', () => {
      this.voters = this.$datePollUtils.computeVoters(this.event);
      this.isVoting = false;
    });

    if (this.currentUserAttendee || !this.isAttendee) {
      this.voters = this.$datePollUtils.computeVoters(this.event);
    } else {
      this.loading = true;
      this.$identityService.getIdentityById(this.currentUserId)
        .then(identity => {
          this.event.attendees.push({
            identity,
            response: this.hasVoted && 'TENTATIVE' || 'NEEDS_ACTION',
          });
          this.voters = this.$datePollUtils.computeVoters(this.event);
        })
        .finally(() => this.loading = false);
    }

    this.$eventService.getDatePollsByDates(this.event.start, this.event.end, 'dateOptions,response')
      .then(data => {
        const datePolls = data && data.events;
        if (datePolls && datePolls.length) {
          this.votedDatePolls = [];
          datePolls.forEach(datePoll => {
            if (datePoll.id === this.event.id) {
              return;
            }
            if (datePoll.attendees
                && datePoll.attendees.length
                && datePoll.attendees[0].response === 'TENTATIVE'
                && datePoll.dateOptions
                && datePoll.dateOptions.length) {
              datePoll.dateOptions.forEach(dateOption => {
                if (dateOption.voters && dateOption.voters.indexOf(this.currentUserId) >= 0) {
                  const datePollOption = JSON.parse(JSON.stringify(datePoll));
                  datePollOption.start = dateOption.start;
                  datePollOption.end = dateOption.end;
                  this.votedDatePolls.push(datePollOption);
                }
              });
            }
          });
        }
      });
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
  },
};
</script>