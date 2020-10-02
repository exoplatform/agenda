<template>
  <exo-identity-suggester
    ref="calendarOwnerSuggester"
    v-model="calendarOwner"
    :labels="calendarSuggesterLabels"
    :include-users="false"
    :disabled="currentSpace"
    name="calendarOwnerAutocomplete"
    class="user-suggester calendarOwnerAutocomplete"
    include-spaces
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
      }
    },
  },
  mounted() {
    this.$root.$on('agenda-event-form-opened', () => {
      this.$nextTick().then(() => this.reset());
    });
  },
  methods:{
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
      }
      this.$forceUpdate();
    },
  }
};
</script>