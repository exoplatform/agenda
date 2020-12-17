<template>
  <div class="d-flex flex-row py-2">
    <v-avatar
      height="32"
      min-height="32"
      width="32"
      min-width="32"
      max-width="32"
      size="32"
      class="mx-3 my-auto spaceAvatar space-avatar-header">
      <v-img :src="ownerAvatarUrl" />
    </v-avatar>
    <div class="d-flex flex-grow-1 flex-column align-left">
      <strong :title="event.summary" class="event-header-title text-truncate">
        {{ event.summary }}
      </strong>
      <div class="text-truncate d-flex">
        <span>{{ $t('agenda.label.in') }}</span>
        <a :href="calendarOwnerLink" class="text-truncate calendar-owner-link pl-1">{{ ownerDisplayName }}</a>
      </div>
    </div>
    <div class="d-flex flex-grow-0">
      <v-menu
        v-if="canEdit"
        bottom
        left
        offset-y>
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            icon
            class="my-auto mr-2"
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
        class="my-auto mr-2"
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
    ownerDisplayName() {
      return this.ownerProfile && (this.ownerProfile.displayName || this.ownerProfile.fullname || this.ownerProfile.fullName);
    },
  },
};
</script>