<template>
  <v-row class="event-details-header d-flex align-center flex-nowrap text-left col-12">
    <v-col class="event-title title text-truncate col-auto px-0 mx-2">
      {{ event.summary }}
    </v-col>
    <v-col class="flex-grow-0 flex-shrink-0 px-0 mx-2">
      {{ $t('agenda.label.in') }}
    </v-col>
    <v-col class="flex-grow-0 flex-shrink-0 px-0 mx-2">
      <v-avatar
        height="32"
        min-height="32"
        width="32"
        min-width="32"
        size="32"
        class="spaceAvatar space-avatar-header">
        <v-img :src="ownerAvatarUrl" />
      </v-avatar>
    </v-col>
    <v-col class="px-0 col-auto calendar-owner-link-parent">
      <a :href="calendarOwnerLink" class="text-truncate d-block">{{ ownerDisplayName }}</a>
    </v-col>
    <v-col class="px-0 flex-grow-1 flex-shrink-0 text-right mx-2">
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