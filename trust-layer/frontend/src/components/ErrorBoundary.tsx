import React from 'react';

type Props = { children: React.ReactNode };
type State = { hasError: boolean };

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="mx-auto my-20 max-w-xl rounded-2xl border border-red-200 bg-red-50 p-8 text-center">
          <h1 className="text-xl font-black text-red-700">Something went wrong</h1>
          <p className="mt-2 text-sm text-red-600">Please refresh this page and try again.</p>
        </div>
      );
    }

    return this.props.children;
  }
}
