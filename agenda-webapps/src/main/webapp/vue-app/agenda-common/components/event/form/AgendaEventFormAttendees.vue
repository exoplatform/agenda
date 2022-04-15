<template>
  <v-flex class="user-suggester text-truncate">
    <form
      ref="form"
      @keypress="checkGuestInvitation($event)">
      <exo-identity-suggester
        ref="invitedAttendeeAutoComplete"
        v-model="invitedAttendee"
        :labels="participantSuggesterLabels"
        :title="suggesterStatus"
        :disabled="disableAttendeeSuggester"
        :ignore-items="ignoredMembers"
        :search-options="searchOptions"
        name="inviteAttendee"
        no-redactor-space
        include-users
        include-spaces />
      <span v-if="disableAttendeeSuggester" class="error--text">
        {{ $t('agenda.suggesterRequired') }}
      </span>
    </form>
        <agenda-notification-alerts v-if="displayAlert" name="event-form" />

    <div v-if="event.attendees" class="identitySuggester no-border mt-0">
      <agenda-event-form-attendee-item
        v-for="attendee in event.attendees"
        :key="attendee.identity.id"
        :attendee="attendee"
        :creator="event.creator"
        @remove-attendee="removeAttendee" />
    </div>
    <div v-if="invitedGuest.length > 0" class="identitySuggester no-border">
      <v-chip
        v-for="email in invitedGuest"
        :key="email"
        class="identitySuggesterItem me-1 mt-3">
        <v-avatar left>
          <v-img :src="defaultAvatar" />
        </v-avatar>
        <span class="text-truncate">
          {{ email }} ({{ $t('agenda.label.guest') }})
        </span>
        <v-btn
          v-exo-tooltip.bottom.body="$t('peopleList.label.clickToDecline')"
          icon
          @click="removeGuestInvitation(email)">
          <v-icon>
            mdi-close-circle
          </v-icon>
        </v-btn>
      </v-chip>     
    </div>
  </v-flex>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      displayAlert: false,
      currentUser: null,
      invitedAttendee: [],
      invitedGuest: [],
      guestFeatureActivated: false
    };
  },
  computed: {
    defaultAvatar() {
      return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/default-image/avatar`;
    },
    searchOptions() {
      return {
        currentUser: '',
        spaceURL: this.event
          && this.event.calendar
          && this.event.calendar.owner
          && this.event.calendar.owner.remoteId,
      };
    },
    participantSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('agenda.searchPlaceholder'),
        placeholder: this.$t('agenda.addParticipants'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    ignoredMembers() {
      return this.event.attendees.map(attendee => `${attendee.identity.providerId}:${attendee.identity.remoteId}`);
    },
    disableAttendeeSuggester() {
      if (!this.event.calendar || !this.event.calendar.owner || !this.event.calendar.owner.remoteId) {
        return true;
      } else {
        return false;
      }
    },
    suggesterStatus(){
      if (this.disableAttendeeSuggester) {
        return this.$t('agenda.suggesterRequired.tooltip');
      } else {
        return '';
      }
    }
  },
  watch: {
    currentUser() {
      this.reset();
    },
    invitedAttendee() {
      if (!this.invitedAttendee) {
        this.$nextTick(this.$refs.invitedAttendeeAutoComplete.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      if (!this.event.attendees) {
        this.event.attendees = [];
      }

      const found = this.event.attendees.find(attendee => {
        return attendee.identity.remoteId === this.invitedAttendee.remoteId
          && attendee.identity.providerId === this.invitedAttendee.providerId;
      });
      if (!found) {
        this.event.attendees.push({
          identity: this.$suggesterService.convertSuggesterItemToIdentity(this.invitedAttendee),
        });
      }
      this.invitedAttendee = null;
    },
  },
  mounted(){
    if (this.event.guestUsers){
      this.invitedGuest = this.event.guestUsers.map(guest => guest.guestEmail);
    } else {
      this.invitedGuest = [];
    }
    this.reset();
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.currentUser = user;
      this.$root.$emit('current-user',this.currentUser);
    });
    this.getGuestFeatureStatus();
  },
  methods: {
    reset() {
      if (!this.event.id && !this.event.occurrence && (!this.event.attendees || !this.event.attendees.length)) { // In case of edit existing event
        // Add current user as default attendee
        if (this.currentUser) {
          this.event.attendees = [{identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatar: this.currentUser.avatar,
              fullname: this.currentUser.fullname,
              external: this.currentUser.external === 'true',
            },
          }}];
        } else {
          this.event.attendees = [];
          this.event.guestUsers = [];          
        }
      }
      this.$emit('initialized');
    },
    removeAttendee(attendee) {
      const index = this.event.attendees.findIndex(addedAttendee => {
        return attendee.identity.remoteId === addedAttendee.identity.remoteId
        && attendee.identity.providerId === addedAttendee.identity.providerId;
      });
      if (index >= 0) {
        this.event.attendees.splice(index, 1);
      }
    },
    checkGuestInvitation(evt) {
      if (this.guestFeatureActivated) {
        const self=this;
        $('form').on('focusout', function(event) {
          setTimeout(function() {
            if (!event.delegateTarget.contains(document.activeElement)) {
              self.saveGuestEmail(event);
            }
          }, 1);
        });
        // eslint-disable-next-line eqeqeq
        if (evt.key == 'Enter') {
          evt.preventDefault();
          this.saveGuestEmail(evt);   }
        // eslint-disable-next-line eqeqeq
        if (evt.keyCode == '32') {
          this.saveGuestEmail(evt);
        }
      } else {
        if (evt.key === 'Enter') {
          evt.preventDefault();
        }
      }
    },
    saveGuestEmail() {
      const reg = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}])|(([\w]+\.)+[a-zA-Z]{2,24}))$/;
      const input = this.$refs.invitedAttendeeAutoComplete.searchTerm;
      const words = input!== null ? input.split(' ') : '';
      const email = words[words.length - 1];
      if (reg.test(email)) {
        if (this.invitedGuest.length > 0 && this.invitedGuest.includes(email)){
          this.displayAlert=true;
          this.$root.$emit('agenda-notification-alert', {
            message: this.$t('Notification.agenda.event.guest'),
            type: 'error',
          }, 'event-form');
        } else {
          this.invitedGuest.push(email);
          this.event.guestUsers.push({
            guestEmail: email });
        } 
      }
       
      this.$refs.invitedAttendeeAutoComplete.searchTerm = null;
    },
    removeGuestInvitation(user) {
      const index = this.invitedGuest.indexOf(user);
      if (index >= 0) {
        this.invitedGuest.splice(index, 1);
        this.event.guestUsers.splice(index, 1);
      }
    },
    getGuestFeatureStatus() {
      this.$featureService.isFeatureEnabled('agenda.guestInEvent').then(status => {
        this.guestFeatureActivated = status;
      });
    }
  }
};
</script>
