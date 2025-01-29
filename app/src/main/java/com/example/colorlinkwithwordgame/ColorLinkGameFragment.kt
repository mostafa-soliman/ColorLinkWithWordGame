package com.example.colorlinkwithwordgame

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import com.example.colorlinkwithwordgame.databinding.FragmentColorLinkGameBinding

class ColorLinkGameFragment : Fragment() {

    private var _binding: FragmentColorLinkGameBinding? = null
    private val binding get() = _binding!!
    private var currentLevel = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            showExitConfirmationDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColorLinkGameBinding.inflate(inflater, container, false)

        setupButtons()

        binding.gameView.setLevelCompleteListener {
            onLevelComplete()
        }

        binding.gameView.post {
            startLevel(currentLevel)
        }

        return binding.root
    }

    private fun setupButtons() {
        binding.exitButton.setOnClickListener {
            showExitConfirmationDialog()
        }

        binding.nextLevelButton.setOnClickListener {
            currentLevel++
            binding.nextLevelButton.visibility = View.GONE
            binding.gameView.resetLevel()
            startLevel(currentLevel)
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Exit Game")
            .setMessage("Are you sure you want to exit the game?")
            .setPositiveButton("Yes") { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun onLevelComplete() {
        binding.nextLevelButton.visibility = View.VISIBLE

        AlertDialog.Builder(requireContext())
            .setTitle("Level Complete!")
            .setMessage("Congratulations! You've completed level $currentLevel")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startLevel(level: Int) {
        binding.gameView.setupLevel(level)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}