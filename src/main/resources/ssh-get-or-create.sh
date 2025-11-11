#!/bin/bash

# Define the email address to check
EMAIL="$1"  # Accept the email as the first argument

# Check if the email argument is provided
if [ -z "$EMAIL" ]; then
  echo "Usage: $0 <email>"
  exit 1
fi

# Check if the .ssh directory exists
if [ ! -d "$HOME/.ssh" ]; then
  echo "No SSH directory found."

  ssh-keygen -t rsa -b 4096 -C "$1" -f ~/.ssh/id_rsa -N ""
  eval "$(ssh-agent -s)"
  ssh-add ~/.ssh/id_rsa
  echo "### Here is your SSH-KEY:"
  echo $(cat ~/.ssh/id_rsa.pub)
  exit 0
fi

# Find the public key(s) in the .ssh directory
for keyfile in "$HOME/.ssh"/*.pub; do
  # Skip if no .pub files are found
  [ -e "$keyfile" ] || continue

  # Check if the public key file contains the email
  if grep -q "$EMAIL" "$keyfile"; then
    echo "SSH key found for email: $EMAIL"
	echo "### Here is your SSH-KEY:"
    echo $(cat ~/.ssh/id_rsa.pub)
    exit 1  # Return 1 if a matching key is found
  fi
done

# If no matching key is found, return 0
echo "No SSH key found for email: $EMAIL"

ssh-keygen -t rsa -b 4096 -C "$1" -f ~/.ssh/id_rsa -N ""
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_rsa
echo "### Here is your SSH-KEY:"
echo $(cat ~/.ssh/id_rsa.pub)

exit 0  # Return 0 if no matching key is found
