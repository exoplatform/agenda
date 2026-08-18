<template>
  <v-scale-transition>
    <v-text-field
      v-model="query"
      :placeholder="$t('agenda.SearchCalendarPlaceholder')"
      :append-icon="appendIcon"
      prepend-inner-icon="fa-filter"
      class="agenda-calendar-filter-input pa-0 my-auto"
      @click:append="query = null" />
  </v-scale-transition>
</template>

<script>
export default {
  props: {
    value: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    startSearchAfterInMilliseconds: 600,
    endTypingKeywordTimeout: 50,
    startTypingKeywordTimeout: 0,
    query: null,
    loading: false,
  }),
  computed: {
    appendIcon() {
      return this.query && 'mdi-close primary--text' || null;
    },
  },
  watch: {
    query() {
      if (!this.query) {
        this.loading = false;
        this.$emit('input', '');
        return;
      }
      this.startTypingKeywordTimeout = Date.now();
      if (!this.loading) {
        this.loading = true;
        this.waitForEndTyping();
      }
    },
  },
  methods: {
    /**
     * Debounces the keyword typing: the query is emitted to the parent only
     * once the user stopped typing for a while.
     * @returns {void}
     */
    waitForEndTyping() {
      window.setTimeout(() => {
        if (Date.now() - this.startTypingKeywordTimeout > this.startSearchAfterInMilliseconds) {
          this.loading = false;
          this.$emit('input', this.query);
          return;
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
  },
};
</script>