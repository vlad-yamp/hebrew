package com.example.hebrew.ui.learning

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.hebrew.R
import com.example.hebrew.databinding.FragmentLearningBinding
import kotlin.math.abs

class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeAndTap()
        setupButtons()
        observeState()
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LearningState.Loading -> {
                    binding.layoutAllLearned.visibility = View.GONE
                    binding.cardContainer.visibility = View.GONE
                    binding.btnKnown.visibility = View.GONE
                    binding.btnUnknown.visibility = View.GONE
                    binding.tvProgress.text = ""
                    binding.tvSwipeHint.visibility = View.GONE
                }
                is LearningState.AllLearned -> {
                    binding.layoutAllLearned.visibility = View.VISIBLE
                    binding.cardContainer.visibility = View.GONE
                    binding.btnKnown.visibility = View.GONE
                    binding.btnUnknown.visibility = View.GONE
                    binding.tvSwipeHint.visibility = View.GONE
                }
                is LearningState.ShowCard -> {
                    binding.layoutAllLearned.visibility = View.GONE
                    binding.cardContainer.visibility = View.VISIBLE
                    binding.btnKnown.visibility = View.VISIBLE
                    binding.btnUnknown.visibility = View.VISIBLE
                    binding.tvSwipeHint.visibility = View.VISIBLE
                    binding.tvProgress.text = getString(R.string.learning_progress, state.current, state.total)
                    binding.tvHebrewWord.text = state.card.hebrew
                    binding.tvRussianWord.text = state.card.russian
                    if (state.isFlipped) showBack() else showFront()
                }
            }
        }
    }

    private fun showFront() {
        binding.cardFront.visibility = View.VISIBLE
        binding.cardBack.visibility = View.GONE
        binding.cardFront.rotationY = 0f
    }

    private fun showBack() {
        binding.cardFront.visibility = View.GONE
        binding.cardBack.visibility = View.VISIBLE
        binding.cardBack.rotationY = 0f
    }

    private fun flipCard() {
        val state = viewModel.state.value as? LearningState.ShowCard ?: return
        if (state.isFlipped) return

        val density = resources.displayMetrics.density
        binding.cardFront.cameraDistance = 8000 * density
        binding.cardBack.cameraDistance = 8000 * density

        val flipOut = ObjectAnimator.ofFloat(binding.cardFront, "rotationY", 0f, -90f).apply { duration = 180 }
        val flipIn = ObjectAnimator.ofFloat(binding.cardBack, "rotationY", 90f, 0f).apply { duration = 180 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                viewModel.flipCard()
                binding.cardFront.visibility = View.GONE
                binding.cardBack.visibility = View.VISIBLE
                flipIn.start()
            }
        })
        flipOut.start()
    }

    private fun setupButtons() {
        binding.btnKnown.setOnClickListener {
            animateSwipe(toRight = true) { viewModel.markKnown() }
        }
        binding.btnUnknown.setOnClickListener {
            animateSwipe(toRight = false) { viewModel.markUnknown() }
        }
        binding.btnRestartLearning.setOnClickListener { viewModel.restartLearning() }
    }

    private fun setupSwipeAndTap() {
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY = 80

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                flipCard()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startX = e1?.x ?: return false
                val diffX = e2.x - startX
                val diffY = e2.y - (e1.y)
                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY
                ) {
                    if (diffX > 0) animateSwipe(true) { viewModel.markKnown() }
                    else animateSwipe(false) { viewModel.markUnknown() }
                    return true
                }
                return false
            }
        })

        // Handle all card touches on the container level
        binding.cardContainer.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        binding.cardFront.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        binding.cardBack.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun animateSwipe(toRight: Boolean, onEnd: () -> Unit) {
        val direction = if (toRight) 1f else -1f
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val slide = ObjectAnimator.ofFloat(
            binding.cardContainer, "translationX", 0f, direction * screenWidth
        ).apply { duration = 250 }
        val fade = ObjectAnimator.ofFloat(binding.cardContainer, "alpha", 1f, 0f).apply { duration = 250 }

        AnimatorSet().apply {
            playTogether(slide, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.cardContainer.translationX = 0f
                    binding.cardContainer.alpha = 1f
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
