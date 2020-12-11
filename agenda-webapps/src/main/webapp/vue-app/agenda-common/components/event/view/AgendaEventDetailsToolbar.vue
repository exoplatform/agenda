<template>
  <v-row class="event-details-header d-flex align-center flex-nowrap text-left col-12">
    <v-col class="event-title title text-truncate col-auto pl-4 mx-2">
      {{ event.summary }}
    </v-col>
    <v-col class="flex-grow-0 flex-shrink-0 px-0 mx-2">
      {{ $t('agenda.label.in') }}
    </v-col>
    <v-col class="flex-grow-1 flex-shrink-0 d-flex px-0 mx-2">
      <exo-space-avatar
        :space="ownerProfile"
        :size="32"
        :labels="labels"
        class="align-center flex-grow-0 flex" />
    </v-col>
    <v-col class="px-0 flex-grow-1 flex-shrink-0 mx-2">
      <template v-if="isAttendee && !isMobile">
        <agenda-event-attendee-buttons
          ref="eventAttendeeButtons"
          :event="event" />
      </template>
    </v-col>
    <v-col class="px-0 flex-grow-0 flex-shrink-0 text-right mx-2">
      <v-menu
        v-if="canEdit"
        bottom
        left
        offset-y>
        <template v-slot:activator="{ on, attrs }">
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
          <v-list-item v-if="connectedConnector" @click="$emit('synchronize')">
            <v-list-item-title :title="$t('agenda.tooltip.synchronizeEvent')">
              {{ $t('agenda.details.header.menu.synchronize') }}
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-col>
    <v-col class="px-0 flex-grow-0 flex-shrink-0 mr-2">
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
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
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
    isAttendee() {
      return this.event.acl && this.event.acl.attendee;
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
    ownerDisplayName() {
      return this.ownerProfile && (this.ownerProfile.displayName || this.ownerProfile.fullname || this.ownerProfile.fullName);
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
};
</script>