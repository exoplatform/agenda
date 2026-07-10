<template>
  <v-list-item class="px-0">
    <v-list-item-avatar
      :tile="isSpace"
      :class="isSpace && 'rounded'"
      size="36"
      class="me-2 my-1 flex-shrink-0">
      <v-img
        :src="avatarUrl"
        :lazy-src="defaultAvatarUrl" />
    </v-list-item-avatar>
    <v-list-item-content class="py-1">
      <v-list-item-title>
        <span class="text-truncate">{{ displayName }}</span>
      </v-list-item-title>
    </v-list-item-content>
    <v-list-item-action class="d-flex flex-row align-center my-0">
      <v-tooltip v-if="isOwner" bottom>
        <template #activator="{ on }">
          <v-icon
            size="20"
            class="me-2 grey-lighten1"
            v-on="on">
            fas fa-crown
          </v-icon>
        </template>
        <span>{{ $t('agenda.eventCreator') }}</span>
      </v-tooltip>
      <v-icon
        v-if="attendee.response"
        size="20"
        :color="responseColor"
        :class="['me-1', responseColorClass]"
        :title="responseLabel">
        {{ responseIcon }}
      </v-icon>
      <v-btn
        v-if="editable"
        :disabled="isOwner"
        :title="isOwner ? $t('agenda.tooltip.cannotRemoveOwner') : $t('agenda.tooltip.removeParticipant')"
        icon
        small
        @click="!isOwner && $emit('remove-attendee', attendee)">
        <v-icon
          size="19"
          :color="isOwner ? 'grey lighten-1' : null"
          :class="!isOwner && 'error-color'">
          fas fa-trash
        </v-icon>
      </v-btn>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
export default {
  props: {
    attendee: {
      type: Object,
      default: () => ({}),
    },
    creator: {
      type: Object,
      default: () => ({}),
    },
    editable: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    defaultAvatarUrl() {
      return '/portal/rest/v1/social/users/default-image/avatar';
    },
    isSpace() {
      return !!(this.attendee.identity && this.attendee.identity.providerId === 'space');
    },
    isOwner() {
      if (this.creator && this.creator.id) {
        return Number(this.attendee.identity.id) === Number(this.creator.id);
      }
      return Number(this.attendee.identity.id) === Number(eXo.env.portal.userIdentityId);
    },
    avatarUrl() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      return profile && (profile.avatarUrl || profile.avatar) || this.defaultAvatarUrl;
    },
    displayName() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      const fullName = profile && (profile.displayName || profile.fullname || profile.fullName);
      return this.isExternal ? `${fullName} (${this.$t('profile.External')})` : fullName;
    },
    isExternal() {
      const profile = this.attendee.identity && this.attendee.identity.profile;
      return profile && (profile.dataEntity && profile.dataEntity.external === 'true' || profile.external);
    },
    responseIcon() {
      switch (this.attendee.response) {
      case 'ACCEPTED': return 'fas fa-check-circle';
      case 'TENTATIVE': return 'fas fa-question-circle';
      case 'DECLINED': return 'fas fa-times-circle';
      default: return 'fas fa-info-circle';
      }
    },
    responseColor() {
      switch (this.attendee.response) {
      case 'ACCEPTED': return null;
      case 'TENTATIVE': return null;
      case 'DECLINED': return null;
      default: return 'grey';
      }
    },
    responseColorClass() {
      switch (this.attendee.response) {
      case 'ACCEPTED': return 'success-color';
      case 'TENTATIVE': return 'primary--text';
      case 'DECLINED': return 'error-color';
      default: return null;
      }
    },
    responseLabel() {
      switch (this.attendee.response) {
      case 'ACCEPTED': return this.$t('agenda.attendee.title.accepted');
      case 'TENTATIVE': return this.$t('agenda.attendee.title.tentative');
      case 'DECLINED': return this.$t('agenda.attendee.title.declined');
      default: return this.$t('agenda.attendee.title.needs.action');
      }
    },
  },
};
</script>