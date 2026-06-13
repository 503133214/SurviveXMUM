// v-reveal — fades + slides an element into view on scroll, once.
// Usage: v-reveal  |  v-reveal="{ delay: 120 }"  (delay in ms for staggering)
const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const reveal = {
  mounted(el, binding) {
    if (reduceMotion) {
      el.classList.add('reveal', 'reveal-in')
      return
    }
    el.classList.add('reveal')
    const delay = binding.value?.delay
    if (delay) el.style.transitionDelay = `${delay}ms`

    const io = new IntersectionObserver(
      (entries, obs) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            el.classList.add('reveal-in')
            obs.unobserve(el)
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -48px 0px' }
    )
    io.observe(el)
    el._revealIO = io
  },
  unmounted(el) {
    if (el._revealIO) el._revealIO.disconnect()
  },
}
