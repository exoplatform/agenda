<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <div class="d-flex align-center flex-grow-1 flex-wrap">
    <v-icon size="16" class="icon-default-color me-2">fas fa-users</v-icon>
    <span class="body-2 me-2">{{ $t('agenda.participants') }}</span>
    <v-btn
      :title="$t('agenda.addParticipants')"
      icon
      x-small
      @click="openDrawer">
      <v-icon size="16" class="icon-default-color">fas fa-plus</v-icon>
    </v-btn>
    <div
      v-if="sortedAttendees.length"
      class="d-flex align-center ms-2 agenda-attendees-strip"
      @mouseenter="showSeeMore = true"
      @mouseleave="showSeeMore = false"
      @focusin="showSeeMore = true"
      @focusout="showSeeMore = false">
      <v-tooltip
        v-for="attendee in visibleAttendees"
        :key="attendee.identity.id"
        bottom>
        <template #activator="{ on }">
          <v-avatar
            :tile="attendee.identity.providerId === 'space'"
            size="26"
            class="me-1"
            style="cursor:pointer"
            v-on="on"
            @click="openDrawer">
            <v-img :src="getAvatarUrl(attendee)" :lazy-src="defaultAvatarUrl" />
          </v-avatar>
        </template>
        <span>{{ getDisplayName(attendee) }}</span>
      </v-tooltip>
      <v-btn
        v-if="overflowCount > 0 && !showSeeMore"
        x-small
        text
        class="caption px-1 min-width-unset"
        @click="openDrawer">
        +{{ overflowCount }}
      </v-btn>
      <v-btn
        v-if="showSeeMore"
        x-small
        text
        class="caption px-1"
        @click="openDrawer">
        {{ $t('agenda.timeline.seeMore') }}
      </v-btn>
    </div>
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      @initialized="$emit('initialized')" />
  </div>
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
      showSeeMore: false,
      maxVisible: 3,
    };
  },
  computed: {
    defaultAvatarUrl() {
      return '/portal/rest/v1/social/users/default-image/avatar';
    },
    sortedAttendees() {
      if (!this.event.attendees) { return []; }
      const creatorId = this.event.creator && this.event.creator.id;
      const ownerId = creatorId || eXo.env.portal.userIdentityId;
      const owner = this.event.attendees.find(a => Number(a.identity.id) === Number(ownerId));
      const others = this.event.attendees
        .filter(a => a !== owner)
        .sort((a, b) => (this.getDisplayName(a) || '').localeCompare(this.getDisplayName(b) || ''));
      return owner ? [owner, ...others] : others;
    },
    visibleAttendees() {
      return this.sortedAttendees.slice(0, this.maxVisible);
    },
    overflowCount() {
      return Math.max(0, this.sortedAttendees.length - this.maxVisible);
    },
  },
  mounted() {
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.$root.$emit('current-user', user);
      if (!this.event.id && !this.event.occurrence && (!this.event.attendees || !this.event.attendees.length)) {
        this.event.attendees = [{
          identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatar: user.avatar,
              fullname: user.fullname,
              external: user.external === 'true',
            },
          },
        }];
      }
      this.$emit('initialized');
    });
  },
  methods: {
    openDrawer() {
      this.$refs.attendeesDrawer.open();
    },
    getAvatarUrl(attendee) {
      const profile = attendee.identity && (attendee.identity.profile || attendee.identity.space);
      return profile && (profile.avatarUrl || profile.avatar) || this.defaultAvatarUrl;
    },
    getDisplayName(attendee) {
      const profile = attendee.identity && (attendee.identity.profile || attendee.identity.space);
      return profile && (profile.displayName || profile.fullname || profile.fullName) || attendee.identity.remoteId;
    },
  },
};
</script>
