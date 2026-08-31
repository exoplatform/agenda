<template>
  <v-row class="event-details-header d-flex align-center flex-nowrap text-left col-12 mx-0">
    <v-col :title="event.summary" class="event-title text-title text-truncate col-auto ps-4 mx-2">
      {{ event.summary }}
    </v-col>
    <v-col class="flex-grow-0 flex-shrink-0 px-0 mx-2">
      {{ $t('agenda.label.in') }}
    </v-col>
    <v-col class="flex-grow-1 text-truncate flex-shrink-0 d-flex px-0 mx-2">
      <!-- an event fetched live from a connected account holds no eXo
           calendar at all: it reads as the account it came from -->
      <div v-if="isRemoteEvent" class="d-flex">
        <!-- the shared connector identity: uploaded image, else the
             admin-chosen font icon, else the packaged avatar — never a
             bare <img> that ignores the configured icon -->
        <!-- the account the event belongs to, not "the" connected one:
             several can be connected at once -->
        <agenda-connector-avatar
          :connector="eventConnector"
          class="spaceAvatar my-auto me-3"
          size="32" />
        <!-- the collection the event actually lives in, never the generic
             "Personal Calendar": a connected account holds several, and a
             label that names none of them identifies nothing. The title
             carries the collection href, which is what tells two same-named
             collections apart when one has to be tracked down. -->
        <div
          :title="remoteCalendarTitle"
          class="flex-grow-0 flex-shrink-0 my-auto text-truncate remote-calendar-label">
          {{ remoteCalendarLabel }}
        </div>
      </div>
      <!-- a space calendar reads as the space owning it: space avatar and
           space display name, exactly as before -->
      <exo-space-avatar
        v-else-if="isSpaceCalendar"
        :space="ownerSpace"
        :size="32"
        popover />
      <!-- a personal calendar reads as the calendar, not as its owner: a
           user owns several of them and only their names tell them apart.
           The owner avatar says whose calendar it is, the label says which
           one — falling back to the owner name for the unnamed default. -->
      <div v-else-if="isUserCalendar" class="d-flex text-truncate">
        <exo-user-avatar
          :identity="ownerUserProfile"
          :size="32"
          avatar
          popover />
        <a
          :href="calendarOwnerLink"
          :title="ownerDisplayName"
          class="text-truncate calendar-owner-link my-auto">{{ ownerDisplayName }}</a>
      </div>
    </v-col>
    <v-col class="px-0 flex-grow-1 flex-shrink-0 mx-2">
      <template v-if="!isTentativeEvent && isAttendee && !$root.isMobile">
        <agenda-event-attendee-buttons
          ref="eventAttendeeButtons"
          :event="event" />
      </template>
    </v-col>
    <extension-registry-components
      :params="params"
      name="AgendaEvent"
      type="agenda-event-header"
      parent-element="div"
      element="div" />
    <v-col class="px-0 flex-grow-0 flex-shrink-0 text-right mx-2">
      <v-menu
        v-if="canEdit"
        v-model="eventMenu"
        eager
        bottom
        left
        offset-y>
        <template #activator="{ on, attrs }">
          <v-btn
            icon
            v-bind="attrs"
            v-on="on">
            <v-icon>mdi-dots-vertical</v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item v-if="canEdit" @click="$emit('edit')">
            <v-list-item-title>
              {{ $t('agenda.details.header.menu.edit') }}
            </v-list-item-title>
          </v-list-item>
          <v-list-item v-if="canEdit" @click="$emit('delete')">
            <v-list-item-title>
              {{ $t('agenda.details.header.menu.delete') }}
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-col>
    <v-col class="px-0 flex-grow-0 flex-shrink-0 me-2">
      <v-btn
        color="grey"
        icon
        @click="$emit('close')">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </v-col>
  </v-row>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
    connectedConnector: {
      type: Object,
      default: () => null
    },
    isAttendee: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    eventMenu: null,
    resolvedRemoteCalendarName: null,
  }),
  computed: {
    calendarOwnerLink() {
      if (this.owner) {
        if (this.owner.providerId === 'organization') {
          return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.owner.remoteId}`;
        } else if (this.owner.providerId === 'space') {
          return `${eXo.env.portal.context}/g/:spaces:${this.owner.remoteId}/`;
        }
      }
      return '';
    },
    canEdit() {
      return this.event.acl && this.event.acl.canEdit;
    },
    owner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    /**
     * Whether the calendar holding the event belongs to a space, the only
     * case an {@code exo-space-avatar} can render: it reads
     * {@code displayName}, {@code avatarUrl} and {@code prettyName}, none of
     * which a user profile carries.
     *
     * @returns {Boolean} true when the calendar owner is a space
     */
    isSpaceCalendar() {
      return !!this.owner && this.owner.providerId === 'space';
    },
    /**
     * Whether the calendar holding the event belongs to a user — a personal
     * calendar, be it the implicit default one or one materialised from a
     * connected CalDAV/Google/O365 account.
     *
     * @returns {Boolean} true when the calendar owner is a user
     */
    isUserCalendar() {
      return !!this.owner && this.owner.providerId === 'organization';
    },
    /**
     * The space owning the calendar, when there is one.
     *
     * @returns {Object} the space entity, or a falsy value for a personal
     *          calendar
     */
    ownerSpace() {
      return this.owner && this.owner.space;
    },
    /**
     * The user profile owning the calendar, when there is one.
     *
     * @returns {Object} the user profile entity, or a falsy value for a
     *          space calendar
     */
    ownerUserProfile() {
      return this.owner && this.owner.profile;
    },
    ownerProfile() {
      return this.ownerUserProfile || this.ownerSpace;
    },
    ownerAvatarUrl() {
      return this.ownerProfile && (this.ownerProfile.avatar || this.ownerProfile.avatarUrl);
    },
    /**
     * The label of the calendar holding the event: the calendar's
     * user-defined name when it has one — two named personal calendars must
     * be tellable apart — else the owner display name exactly as before.
     *
     * @returns {String} calendar display label
     */
    ownerDisplayName() {
      const calendarName = this.event && this.event.calendar && this.event.calendar.name;
      if (calendarName) {
        return calendarName;
      }
      return this.ownerProfile && (this.ownerProfile.displayName || this.ownerProfile.fullname || this.ownerProfile.fullName);
    },
    isRemoteEvent(){
      return this.event.type === 'remoteEvent';
    },
    /**
     * The account the event was read from: the one carried by the event
     * itself, since several can be connected at once and the header must name
     * the one this event came from, not whichever happens to be first.
     *
     * @returns {Object} the connector, or a falsy value for a stored event
     */
    eventConnector() {
      return this.event && this.event.connector || this.connectedConnector;
    },
    /**
     * The href of the collection the event was read from. It is the only
     * thing a live read carries about where the event lives — there is no eXo
     * calendar behind it.
     *
     * @returns {String} the collection href, empty when the read carried none
     */
    remoteCalendarHref() {
      return this.event && this.event.calendarId || '';
    },
    /**
     * The account's own identifier, as the connector reports it — a mailbox
     * or a login, not the connector's name. Used only to say *whose* account
     * a collection belongs to when the collection itself cannot be named.
     *
     * @returns {String} the account, empty when the connector reports none
     */
    remoteAccount() {
      return this.eventConnector && this.eventConnector.user || '';
    },
    /**
     * What the header says a live-read event is in.
     *
     * <p>
     * The collection's own name whenever the account can give it. When it
     * cannot — the account is unreachable, or the collection is one the
     * connector reads from but leaves out of its listing — the header says so
     * in terms of the account instead of inventing a name. What it never does
     * again is claim a "Personal Calendar" that exists nowhere, which is the
     * label that made a stray event impossible to place.
     *
     * @returns {String} the label to display
     */
    remoteCalendarLabel() {
      if (this.resolvedRemoteCalendarName) {
        return this.resolvedRemoteCalendarName;
      }
      if (this.remoteAccount) {
        return this.$t('agenda.remoteEvent.calendarOfAccount', {0: this.remoteAccount});
      }
      return this.$t('agenda.remoteEvent.unnamedCalendar');
    },
    /**
     * The hover text of the label: the collection href, which is the only
     * thing that distinguishes two collections sharing a display name — and
     * the thing one needs when tracking down where an unexpected event
     * actually came from.
     *
     * @returns {String} the tooltip, the label itself when there is no href
     */
    remoteCalendarTitle() {
      if (!this.remoteCalendarHref) {
        return this.remoteCalendarLabel;
      }
      return this.$t('agenda.remoteEvent.calendarLocation', {0: this.remoteCalendarHref});
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
    isTentativeEvent() {
      return this.event && this.event.status === 'TENTATIVE';
    },
    params() {
      return {
        event: this.event,
        isAttendee: this.isAttendee,
        connectedConnector: this.connectedConnector,
      };
    },
  },
  watch: {
    /**
     * The dialog is reused from one event to the next, so a new event must
     * re-resolve rather than keep the previous event's calendar name.
     *
     * @returns {void}
     */
    event() {
      this.resolveRemoteCalendarName();
    },
  },
  created() {
    this.resolveRemoteCalendarName();
  },
  mounted() {
    $('.agendaEventDialog').parent().click(() => {
      this.eventMenu = false;
    });
  },
  methods: {
    /**
     * Asks the account what it calls the collection the event was read from.
     *
     * <p>
     * Only for a live read: a stored event already names its calendar through
     * the owner header and must not pay for a request. The answer is dropped
     * unless it still matches the collection on display — the dialog can be
     * moved to another event while a listing is in flight, and a name landing
     * on the wrong event is worse than the honest fallback.
     *
     * @returns {void}
     */
    resolveRemoteCalendarName() {
      this.resolvedRemoteCalendarName = null;
      if (!this.isRemoteEvent) {
        return;
      }
      const requestedHref = this.remoteCalendarHref;
      this.$remoteEventConnector.remoteCalendarName(this.eventConnector, requestedHref)
        .then(name => {
          if (requestedHref === this.remoteCalendarHref) {
            this.resolvedRemoteCalendarName = name;
          }
        });
    },
  },
};
</script>