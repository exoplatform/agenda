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
        <!--
          The search field IS this drawer, so it is on screen when the drawer
          opens, on a line of its own and at full width.

          It used to sit behind a "+ Add" button whose only statement was
          showSuggester = true. That button existed to make room: the add
          control and the filter were sharing one line, so one of them had to
          be a toggle. Giving each its own line leaves the toggle nothing to
          solve, and it goes — along with the back arrow beside the field,
          whose only job was to return to the line the filter button was on.
        -->
        <form
          v-if="editable"
          ref="form"
          class="mb-4"
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
            dense
            include-spaces />
          <span v-if="disableAttendeeSuggester" class="error--text caption">
            {{ $t('agenda.suggesterRequired') }}
          </span>
        </form>
        <!--
          The filter keeps its toggle, but on its own line rather than in
          place of the field above.

          It is NOT folded away with showSuggester, because the two flags were
          doing different work despite looking alike: showSuggester only ever
          bought horizontal room, while showFilterBar keeps a text field and a
          status dropdown out of a drawer that mostly does not need them —
          most events have a handful of attendees and nothing to filter. What
          changes is only that expanding the filter no longer costs the search
          field its place.
        -->
        <div v-if="!showFilterBar" class="d-flex align-center mb-4">
          <div v-if="!editable" class="ms-1 text-color subtitle-2">
            {{ $t('agenda.label.searchParticipant') }}
          </div>
          <v-spacer />
          <v-btn
            icon
            small
            class="flex-shrink-0"
            :title="$t('agenda.label.filterParticipants')"
            @click="openFilterBar">
            <v-icon
              size="20"
              :color="filterButtonColor"
              :class="!filterButtonColor && 'icon-default-color'">
              fas fa-filter
            </v-icon>
          </v-btn>
        </div>
        <div v-else class="d-flex align-center mb-4">
          <v-btn
            icon
            small
            class="me-2 flex-shrink-0"
            @click="closeFilterBar">
            <v-icon size="20" class="icon-default-color">fas fa-arrow-left</v-icon>
          </v-btn>
          <v-text-field
            ref="filterInput"
            v-model="filterText"
            :placeholder="$t('agenda.label.searchParticipants')"
            class="flex-grow-1 pa-0 ma-0"
            dense
            clear-icon="fa-times fa-1x primary--text position-absolute absolute-vertical-center my-0"
            clearable
            hide-details>
            <template #prepend-inner>
              <v-icon size="20" :class="filterText ? 'primary--text' : 'icon-default-color'">fas fa-filter</v-icon>
            </template>
          </v-text-field>
          <v-menu offset-y>
            <template #activator="{ on }">
              <v-btn
                icon
                small
                class="flex-shrink-0 ms-1"
                :title="dropdownTriggerTitle"
                v-on="on">
                <v-icon
                  size="20"
                  :color="dropdownTriggerColor"
                  :class="dropdownTriggerColorClass">
                  {{ dropdownTriggerIcon }}
                </v-icon>
              </v-btn>
            </template>
            <v-list dense>
              <v-list-item
                v-for="option in responseFilterOptions"
                :key="option.value"
                @click="responseFilter = option.value">
                <v-icon
                  size="16"
                  :color="option.color"
                  :class="['me-2', option.colorClass]">
                  {{ option.icon }}
                </v-icon>
                <v-list-item-title>{{ option.label }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </div>
        <div v-if="event && event.attendees" class="mt-2">
          <agenda-event-form-attendee-item
            v-for="attendee in displayedAttendees"
            :key="attendee.identity.id || attendee.identity.remoteId"
            :attendee="attendee"
            :creator="event.creator"
            :editable="editable"
            @remove-attendee="removeAttendee" />
          <div v-if="hasMore" class="d-flex justify-center py-2">
            <v-btn
              text
              small
              @click="loadMore">
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
      showFilterBar: false,
      filterText: '',
      responseFilter: 'ALL',
    };
  },
  computed: {
    responseFilterOptions() {
      return [
        {value: 'ALL', icon: 'fas fa-calendar-check', label: this.$t('agenda.label.all'), title: this.$t('agenda.filter.title.all'), color: null, colorClass: null},
        {value: 'ACCEPTED', icon: 'fas fa-check-circle', label: this.$t('agenda.accepted'), title: this.$t('agenda.filter.title.accepted'), color: null, colorClass: 'success-color'},
        {value: 'TENTATIVE', icon: 'fas fa-question-circle', label: this.$t('agenda.tentative'), title: this.$t('agenda.filter.title.tentative'), color: null, colorClass: 'primary--text'},
        {value: 'DECLINED', icon: 'fas fa-times-circle', label: this.$t('agenda.declined'), title: this.$t('agenda.filter.title.declined'), color: null, colorClass: 'error-color'},
        {value: 'NEEDS_ACTION', icon: 'fas fa-info-circle', label: this.$t('agenda.needs_action'), title: this.$t('agenda.filter.title.needs.action'), color: 'grey', colorClass: null},
      ];
    },
    activeStatusOption() {
      return this.responseFilter !== 'ALL'
        && this.responseFilterOptions.find(option => option.value === this.responseFilter)
        || null;
    },
    filterButtonColor() {
      return this.isFiltered ? 'primary' : null;
    },
    dropdownTriggerIcon() {
      return (this.activeStatusOption && this.activeStatusOption.icon) || 'fas fa-calendar-check';
    },
    dropdownTriggerTitle() {
      const option = this.responseFilterOptions.find(item => item.value === this.responseFilter);
      return option && option.title;
    },
    dropdownTriggerColor() {
      if (this.activeStatusOption) {
        return this.activeStatusOption.color;
      }
      return this.isFiltered ? 'primary' : null;
    },
    dropdownTriggerColorClass() {
      if (this.activeStatusOption) {
        return this.activeStatusOption.colorClass;
      }
      return this.dropdownTriggerColor ? null : 'icon-default-color';
    },
    isFiltered() {
      return !!this.filterText || this.responseFilter !== 'ALL';
    },
    filteredAttendees() {
      let attendees = this.sortedAttendees;
      if (this.responseFilter !== 'ALL') {
        attendees = attendees.filter(attendee => (attendee.response || 'NEEDS_ACTION') === this.responseFilter);
      }
      const term = this.filterText && this.filterText.trim().toLowerCase();
      if (term) {
        attendees = attendees.filter(attendee => {
          const profile = attendee.identity.profile || attendee.identity.space;
          const name = (profile && (profile.fullname || profile.fullName || profile.displayName)) || '';
          return name.toLowerCase().includes(term);
        });
      }
      return attendees;
    },
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
      return this.filteredAttendees.slice(0, this.displayedCount);
    },
    hasMore() {
      return this.filteredAttendees.length > this.displayedCount;
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
    /*
     * The three strings the suggester shows, and they land in three different
     * places — which is why they are not interchangeable:
     *
     *   placeholder       the field itself, before anything is typed. Since
     *                     EXO-89852 put this field on screen the moment the
     *                     drawer opens, it is the first thing anybody reads
     *                     here, so it says what to DO and names all three
     *                     things the field takes — including the email
     *                     address, which the field has always accepted
     *                     through checkGuestInvitation and which no text on
     *                     this screen used to mention.
     *   searchPlaceholder the dropdown, before a search has started. Generic,
     *                     and shared with the calendar-owner and timeline
     *                     suggesters, so it stays as it is.
     *   noDataLabel       the dropdown, when a search found nothing. This one
     *                     is the drawer's OWN key: the shared agenda.noDataLabel
     *                     says "No agenda found", which is right for the two
     *                     suggesters that search agendas and wrong for the one
     *                     searching people.
     */
    participantSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('agenda.searchPlaceholder'),
        placeholder: this.$t('agenda.attendees.searchPlaceholder'),
        noDataLabel: this.$t('agenda.attendees.noDataLabel'),
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
    /**
     * Opens the drawer, with the caller's response filter if it named one, and
     * puts the cursor in the search field.
     *
     * The focus is done here rather than through the suggester's own autofocus
     * prop, which it does support. That prop is a mount-time hook, and
     * exo-drawer keeps its content mounted once opened (its `initialized` flag
     * is never reset), so the prop would put the cursor in the field on the
     * first opening of a page's lifetime and on no other. Removing a click
     * only to leave the organiser reaching for the tab key on every opening
     * afterwards is not the fix that was asked for. focus() is the suggester's
     * own public method — no reaching into its DOM.
     *
     * @param {String} responseFilter the response to filter the list on, if any
     * @returns {void}
     */
    open(responseFilter) {
      this.displayedCount = PAGE_SIZE;
      this.showFilterBar = false;
      this.filterText = '';
      this.responseFilter = responseFilter || 'ALL';
      this.$refs.attendeesDrawer.open();
      this.$nextTick(() => this.$refs.invitedAttendeeAutoComplete && this.$refs.invitedAttendeeAutoComplete.focus());
    },
    loadMore() {
      this.displayedCount += PAGE_SIZE;
    },
    openFilterBar() {
      this.showFilterBar = true;
      this.$nextTick(() => this.$refs.filterInput && this.$refs.filterInput.focus());
    },
    closeFilterBar() {
      this.showFilterBar = false;
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