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
    <div class="event-header-title d-flex flex-grow-1 flex-column align-left">
      <strong class="text-truncate">
        {{ event.summary }}
      </strong>
      <div class="text-truncate caption">
        {{ $t('agenda.label.createdBy', {0: creatorFullName}) }}
      </div>
    </div>
    <div class="d-flex flex-grow-0">
      <v-menu
        v-if="isAttendee"
        bottom
        left
        offset-y>
        <template v-slot:activator="{ on, attrs }">
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
          <v-list-item v-if="isAttendee" @click="$root.$emit('agenda-date-poll-change-vote')">
            <v-list-item-title>
              {{ $t('agenda.changeVote') }}
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
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    dateTimeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
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
    creatorFullName() {
      return this.event && this.event.creator && this.event.creator.profile && this.event.creator.profile.fullname || '';
    },
  },
};
</script>