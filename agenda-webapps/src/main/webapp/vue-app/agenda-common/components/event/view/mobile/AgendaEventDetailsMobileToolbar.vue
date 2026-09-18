<template>
  <div class="d-flex flex-row py-2">
    <!-- an event fetched live from a connected account holds no eXo calendar
         and no owner at all: it reads as the account it came from, exactly as
         the desktop header reads it (EXO-89825) -->
    <agenda-connector-avatar
      v-if="isRemoteEvent"
      :connector="eventConnector"
      class="mx-3 my-auto"
      size="32" />
    <v-avatar
      v-else
      height="32"
      min-height="32"
      width="32"
      min-width="32"
      max-width="32"
      size="32"
      class="mx-3 my-auto spaceAvatar space-avatar-header">
      <v-img :src="ownerAvatarUrl" />
    </v-avatar>
    <div class="d-block text-truncate flex-grow-1 align-left">
      <strong :title="event.summary" class="event-header-title text-truncate">
        {{ event.summary }}
      </strong>
      <!-- the collection the event actually lives in, never the generic
           "Personal Calendar": a connected account holds several, and a label
           that names none of them identifies nothing. The title carries the
           collection href, which is what tells two same-named collections
           apart when one has to be tracked down. -->
      <div
        v-if="isRemoteEvent"
        :title="remoteCalendarTitle"
        class="text-truncate d-flex">
        <span>{{ $t('agenda.label.in') }}</span>
        <span class="text-truncate ps-1 remote-calendar-label">{{ remoteCalendarLabel }}</span>
      </div>
      <!-- "in" is a preposition, not a line: with nothing to name after it, it
           says nothing and looks broken. The whole line goes rather than the
           label alone -->
      <div v-else-if="ownerDisplayName" class="text-truncate d-flex">
        <span>{{ $t('agenda.label.in') }}</span>
        <a :href="calendarOwnerLink" class="text-truncate calendar-owner-link ps-1">{{ ownerDisplayName }}</a>
      </div>
    </div>
    <div class="d-flex flex-grow-0">
      <v-menu
        v-if="canEdit"
        bottom
        left
        offset-y>
        <template #activator="{ on, attrs }">
          <v-btn
            icon
            class="my-auto me-2"
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
    </div>
    <div class="d-flex flex-grow-0">
      <v-btn
        class="my-auto me-2"
        color="grey"
        icon
        @click="$emit('close')">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </div>
  </div>
</template>
<script>
import remoteEventCalendarMixin from '../../../../js/RemoteEventCalendarMixin.js';

export default {
  mixins: [remoteEventCalendarMixin],
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
    connectedConnector: {
      type: Object,
      default: () => null
    },
  },
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
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
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
  },
};
</script>