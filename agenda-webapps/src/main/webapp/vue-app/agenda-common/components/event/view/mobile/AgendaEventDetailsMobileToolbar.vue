<template>
<v-container>
  <v-row align="start" no-gutters >
    <v-col cols="1" class="mt-1 ml-n2">
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
    </v-col>
    <v-col cols="8" class="text-truncate ml-6">
      <strong :title="event.summary" class="event-header-title">
        {{ event.summary }}
      </strong>
      <div class="text-truncate d-flex">
        <span>{{ $t('agenda.label.in') }}</span>
        <a :href="calendarOwnerLink" class="text-truncate calendar-owner-link ps-1">{{ ownerDisplayName }}</a>
      </div>
    </v-col>
    <v-col cols="1">
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
    </v-col>
    <v-col cols="1" class="ml-n2 mr-2">
      <v-btn
        class="my-auto me-2"
        color="grey"
        icon
        @click="$emit('close')">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </v-col>
  </v-row>
  </v-container>
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