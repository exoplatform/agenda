<template>
  <exo-drawer
    ref="attendeesDrawer"
    right
    body-classes="hide-scroll decrease-z-index-more"
    @closed="$emit('closed')">
    <template slot="title">
      {{ $t('agenda.label.participants') }}
    </template>
    <template slot="content">
      <div class="pa-4">
        <template v-if="editable">
          <div v-if="!showSuggester">
            <v-btn
              color="primary"
              class="mb-4 ms-1"
              elevation="0"
              small
              width="94px"
              @click="showSuggester = true">
              <v-icon size="14" class="me-1">fas fa-plus</v-icon>
              <span class="text-font-size">{{ $t('agenda.label.addParticipants') }}</span>
            </v-btn>
          </div>
          <form
            v-else
            ref="form"
            class="mb-4"
            @keypress="checkGuestInvitation($event)">
            <div class="d-flex align-center">
              <v-btn icon x-small class="me-2 flex-shrink-0" @click="showSuggester = false">
                <v-icon size="16" class="icon-default-color">fas fa-arrow-left</v-icon>
              </v-btn>
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
                dense
                include-spaces />
            </div>
            <span v-if="disableAttendeeSuggester" class="error--text caption">
              {{ $t('agenda.suggesterRequired') }}
            </span>
          </form>
        </template>
        <div v-if="event && event.attendees" class="mt-2">
          <agenda-event-form-attendee-item
            v-for="attendee in displayedAttendees"
            :key="attendee.identity.id || attendee.identity.remoteId"
            :attendee="attendee"
            :creator="event.creator"
            :editable="editable"
            @remove-attendee="removeAttendee" />
          <div v-if="hasMore" class="d-flex justify-center py-2">
            <v-btn text small @click="loadMore">
              {{ $t('agenda.button.loadMore') }}
            </v-btn>
          </div>
        </div>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
const PAGE_SIZE = 20;

export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    editable: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      invitedAttendee: null,
      displayedCount: PAGE_SIZE,
      showSuggester: false,
    };
  },
  computed: {
    sortedAttendees() {
      const attendees = this.event && this.event.attendees || [];
      if (!attendees.length) {
        return [];
      }
      const creatorId = (this.event && this.event.creator && this.event.creator.id)
        || eXo.env.portal.userIdentityId;
      const owner = attendees.find(a => Number(a.identity.id) === Number(creatorId));
      const others = attendees
        .filter(a => Number(a.identity.id) !== Number(creatorId))
        .sort((a1, a2) => {
          const n1 = (a1.identity.profile && (a1.identity.profile.fullname || a1.identity.profile.fullName))
            || (a1.identity.space && a1.identity.space.displayName) || '';
          const n2 = (a2.identity.profile && (a2.identity.profile.fullname || a2.identity.profile.fullName))
            || (a2.identity.space && a2.identity.space.displayName) || '';
          return n1.localeCompare(n2);
        });
      return owner ? [owner, ...others] : others;
    },
    displayedAttendees() {
      return this.sortedAttendees.slice(0, this.displayedCount);
    },
    hasMore() {
      return this.sortedAttendees.length > this.displayedCount;
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
        placeholder: this.$t('agenda.attendees.searchPlaceholder'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    ignoredMembers() {
      return this.event.attendees
        ? this.event.attendees.map(a => `${a.identity.providerId}:${a.identity.remoteId}`)
        : [];
    },
    disableAttendeeSuggester() {
      return !this.event.calendar || !this.event.calendar.owner || !this.event.calendar.owner.remoteId;
    },
    suggesterStatus() {
      return this.disableAttendeeSuggester ? this.$t('agenda.suggesterRequired.tooltip') : '';
    },
  },
  watch: {
    invitedAttendee(val) {
      if (!val) {
        this.$nextTick(this.$refs.invitedAttendeeAutoComplete.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      if (!this.event.attendees) {
        this.event.attendees = [];
      }
      const found = this.event.attendees.find(a =>
        a.identity.remoteId === val.remoteId && a.identity.providerId === val.providerId
      );
      if (!found) {
        this.event.attendees.push({
          identity: this.$suggesterService.convertSuggesterItemToIdentity(val),
          response: 'NEEDS_ACTION',
        });
      }
      this.invitedAttendee = null;
    },
  },
  methods: {
    open() {
      this.displayedCount = PAGE_SIZE;
      this.showSuggester = false;
      this.$refs.attendeesDrawer.open();
    },
    loadMore() {
      this.displayedCount += PAGE_SIZE;
    },
    removeAttendee(attendee) {
      if (!this.event || !this.event.attendees) {
        return;
      }
      const index = this.event.attendees.findIndex(a =>
        a.identity.remoteId === attendee.identity.remoteId
        && a.identity.providerId === attendee.identity.providerId
      );
      if (index >= 0) {
        this.event.attendees.splice(index, 1);
      }
    },
    checkGuestInvitation(evt) {
      const self = this;
      $('form').on('focusout', function(event) {
        setTimeout(function() {
          if (!event.delegateTarget.contains(document.activeElement)) {
            self.saveGuestEmail(event);
          }
        }, 1);
      });
      if (evt.key === 'Enter') {
        evt.preventDefault();
        this.saveGuestEmail(evt);
      }
      if (evt.keyCode === 32) {
        this.saveGuestEmail(evt);
      }
    },
    saveGuestEmail() {
      const reg = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,24}))$/;
      const input = this.$refs?.invitedAttendeeAutoComplete?.searchTerm?.toLowerCase();
      const words = input !== null ? input.split(' ') : '';
      const email = words[words.length - 1];
      if (reg.test(email)) {
        this.event.attendees.push({
          identity: {
            id: `${email}`,
            remoteId: email,
            identityId: email,
            providerId: 'GUEST_USER',
            profile: {
              fullName: email,
              avatarUrl: '/portal/rest/v1/social/users/default-image/avatar',
            },
          },
        });
      }
      this.$refs.invitedAttendeeAutoComplete.clear();
    },
  },
};
</script>