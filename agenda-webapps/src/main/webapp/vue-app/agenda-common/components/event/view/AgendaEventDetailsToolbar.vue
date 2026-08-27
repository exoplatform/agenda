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
          :connector="event.connector || connectedConnector"
          class="spaceAvatar my-auto me-3"
          size="32" />
        <div class="flex-grow-0 flex-shrink-0 my-auto">
          {{ $t('agenda.personalCalendar') }}
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
  mounted() {
    $('.agendaEventDialog').parent().click(() => {
      this.eventMenu = false;
    });
  },
};
</script>