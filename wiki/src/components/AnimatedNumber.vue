<template>
  <span ref="el">{{ display }}</span>
</template>

<script>
// Counts up to `to` when it first scrolls into view.
export default {
  name: "AnimatedNumber",
  props: {
    to: { type: Number, required: true },
    duration: { type: Number, default: 1200 },
  },
  data() {
    return { display: 0 };
  },
  mounted() {
    const reduce =
      window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) {
      this.display = this.to;
      return;
    }
    // Hero stats are above the fold — animate shortly after mount so the value
    // is always correct even where IntersectionObserver wouldn't fire.
    this.timer = setTimeout(() => this.run(), 250);
  },
  beforeUnmount() {
    clearTimeout(this.timer);
  },
  methods: {
    run() {
      const start = performance.now();
      const tick = (now) => {
        const p = Math.min((now - start) / this.duration, 1);
        const eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
        this.display = Math.round(eased * this.to);
        if (p < 1) requestAnimationFrame(tick);
        else this.display = this.to;
      };
      requestAnimationFrame(tick);
    },
  },
};
</script>
