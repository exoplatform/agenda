<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <exo-identity-suggester
    ref="calendarOwnerSuggester"
    v-model="calendarOwner"
    :labels="calendarSuggesterLabels"
    :include-users="false"
    :disabled="currentSpace"
    :width="220"
    name="calendarOwnerAutocomplete"
    class="user-suggester calendarOwnerAutocomplete"
    include-spaces
    only-redactor
    only-manager
    required />
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      calendarOwner: null,
      currentUser: null,
    };
  },
  computed: {
    calendarSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('agenda.searchPlaceholder'),
        placeholder: this.$t('agenda.chooseCalendar'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  watch: {
    calendarOwner() {
      this.resetCustomValidity();

      if (this.calendarOwner) {
        this.event.calendar.owner = {
          remoteId: this.calendarOwner.remoteId,
          providerId: this.calendarOwner.providerId,
        };
        if (this.calendarOwner.profile) {
          this.event.calendar.owner.profile = {
            avatarUrl: this.calendarOwner.profile.avatarUrl,
            fullName: this.calendarOwner.profile.fullName,
          };
        }
      } else {
        this.event.calendar.owner = null;
        if (this.currentUser) {
          this.event.attendees = [{identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatar: this.currentUser.avatar,
              fullname: this.currentUser.fullname,
            },
          }}];
        }
      }
    },
  },
  mounted() {
    this.$root.$once('agenda-event-form-opened', () => {
      this.$nextTick().then(() => this.reset());
    });
    this.$root.$on('current-user',user => {
      this.currentUser = user;
    });
  },
  methods: {
    resetCustomValidity() {
      if (this.$refs.calendarOwnerSuggester) {
        this.$refs.calendarOwnerSuggester.$el.querySelector('input').setCustomValidity('');
      }
    },
    validateForm() {
      this.resetCustomValidity();

      if (this.$refs.calendarOwnerSuggester && !this.event.calendar.owner || !this.event.calendar.owner.id && !(this.event.calendar.owner.providerId && this.event.calendar.owner.remoteId)) {
        this.$refs.calendarOwnerSuggester.$el.querySelector('input').setCustomValidity(this.$t('agenda.message.missingSpaceName'));
      }
    },
    reset() {
      // eslint-disable-next-line no-extra-parens
      if (this.event.id || this.event.occurrence ||  (this.event.calendar && this.event.calendar.owner && (this.event.calendar.owner.id || (this.event.calendar.owner.remoteId && this.event.calendar.owner.providerId)))) { // In case of edit existing event
        this.calendarOwner = this.$suggesterService.convertIdentityToSuggesterItem(this.event.calendar.owner);

        window.setTimeout(() => {
          if (this.$refs.calendarOwnerSuggester) {
            this.$refs.calendarOwnerSuggester.items = [this.calendarOwner];
          }
          this.$emit('initialized');
        }, 200);
      } else { // In case of new event
        if (this.currentSpace) {
          this.calendarOwner = this.event.calendar.owner = {
            id: `space:${this.currentSpace.prettyName}`,
            remoteId: this.currentSpace.prettyName,
            providerId: 'space',
            profile: {
              avatarUrl: this.currentSpace.avatarUrl,
              fullName: this.currentSpace.displayName,
            },
          };
          if (this.$refs.calendarOwnerSuggester) {
            this.$refs.calendarOwnerSuggester.items = [this.calendarOwner];
          }
        } else {
          if (this.$refs.calendarOwnerSuggester) {
            this.$refs.calendarOwnerSuggester.items = [];
          }
          this.calendarOwner = {};
        }
        this.$emit('initialized');
      }
      this.$forceUpdate();
    },
  }
};
</script>